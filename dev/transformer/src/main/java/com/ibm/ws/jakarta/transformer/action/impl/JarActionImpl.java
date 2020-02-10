package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
// import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.ArchiveChanges;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.ClassAction;
import com.ibm.ws.jakarta.transformer.action.JarAction;
import com.ibm.ws.jakarta.transformer.action.ManifestAction;
import com.ibm.ws.jakarta.transformer.action.ServiceConfigAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;
import com.ibm.ws.jakarta.transformer.util.FileUtils;
import com.ibm.ws.jakarta.transformer.util.InputStreamData;

public class JarActionImpl extends ActionImpl implements JarAction {

	public JarActionImpl(ActionImpl parentAction) {
		super(parentAction);
	}

	public JarActionImpl(Set<String> includes, Set<String> excludes, Map<String, String> renames,
			Map<String, String> versions, Map<String, BundleData> bundleUpdates) {
		super(includes, excludes, renames, versions, bundleUpdates);
	}

	public JarActionImpl(
		PrintStream logStream, boolean isTerse, boolean isVerbose,
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		Map<String, String> versions, Map<String, BundleData> bundleUpdates) {

		super(logStream, isTerse, isVerbose,
			   includes, excludes,
			   renames, versions, bundleUpdates);
	}

	//

	public String getName() {
		return "Jar Action";
	}

	//

	@Override
	protected JarChangesImpl newChanges() {
		return new JarChangesImpl();
	}

	@Override
	public JarChangesImpl getChanges() {
		return (JarChangesImpl) super.getChanges();
	}

	protected void recordUnaccepted(String resourceName) {
		verbose( "Resource [ %s ]: Not accepted\n", resourceName );

		getChanges().record();
	}

	protected void recordUnselected(Action action, boolean hasChanges, String resourceName) {
		verbose(
			"Resource [ %s ] Action [ %s ]: Accepted but not selected\n",
			resourceName, action.getName() );

		getChanges().record(action, hasChanges);
	}

	protected void recordTransform(Action action, String resourceName) {
		verbose(
			"Resource [ %s ] Action [ %s ]: Changes [ %s ]\n",
			resourceName, action.getName(), action.hasChanges() );

		getChanges().record(action);
	}

	//

	@Override
	public boolean accept(String resourceName) {
		return resourceName.endsWith(".jar");
	}

	//

	@Override
	public void apply(
		String inputPath, InputStream inputStream,
		String outputPath, OutputStream outputStream) throws JakartaTransformException {

		setResourceNames(inputPath, outputPath);

		// Use Zip streams instead of Jar streams.
		//
		// Jar streams automatically read and consume the manifest, which we don't want.

		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

		try {
			apply(inputPath, zipInputStream, outputPath, zipOutputStream);
			// throws JakartaTransformException

		} finally {
			try {
				zipOutputStream.finish(); // throws IOException
			} catch ( IOException e ) {
				throw new JakartaTransformException("Failed to complete output [ " + outputPath + " ]", e);
			}
		}
	}

	protected void apply(
		String inputPath, ZipInputStream zipInputStream,
		String outputPath, ZipOutputStream zipOutputStream) throws JakartaTransformException {

		String prevName = null;
		String inputName = null;

		try {
			ClassAction classAction = new ClassActionImpl(this);
			ServiceConfigAction configAction = new ServiceConfigActionImpl(this);
			ManifestAction manifestAction = new ManifestActionImpl(this, ManifestActionImpl.IS_MANIFEST);
			ManifestAction featureAction = new ManifestActionImpl(this, ManifestActionImpl.IS_FEATURE);

			byte[] buffer = new byte[FileUtils.BUFFER_ADJUSTMENT];

			ZipEntry inputEntry;
			while ( (inputEntry = zipInputStream.getNextEntry()) != null ) {
				inputName = inputEntry.getName();
				long inputLength = inputEntry.getSize();

				verbose("[ %s.%s ] [ %s ] Size [ %s ]\n",
					getClass().getSimpleName(), "applyZip", inputName, inputLength);

				Action selectedAction;

				if ( classAction.accept(inputName) ) {
					selectedAction = classAction;
				} else if ( configAction.accept(inputName) ) {
					selectedAction = configAction;
				} else if ( manifestAction.accept(inputName) ) {
					selectedAction = manifestAction;
				} else if ( featureAction.accept(inputName) ) {
					selectedAction = featureAction;
				} else {
					selectedAction = null;
				}

				if ( !select(inputName) || (selectedAction == null) ) {
					if ( selectedAction == null ) {
						recordUnaccepted(inputName);
					} else {
						recordUnselected(selectedAction, !ArchiveChanges.HAS_CHANGES, inputName);
					}

					// TODO: Should more of the entry details be transferred?

					ZipEntry outputEntry = new ZipEntry(inputName);
					zipOutputStream.putNextEntry(outputEntry); // throws IOException
					FileUtils.transfer(zipInputStream, zipOutputStream, buffer); // throws IOException 
					zipOutputStream.closeEntry(); // throws IOException

				} else {
//					if ( getIsVerbose() ) {
//						long inputCRC = inputEntry.getCrc();
//
//						int inputMethod = inputEntry.getMethod();
//						long inputCompressed = inputEntry.getCompressedSize();
//
//						FileTime inputCreation = inputEntry.getCreationTime();
//						FileTime inputAccess = inputEntry.getLastAccessTime();
//						FileTime inputModified = inputEntry.getLastModifiedTime();
//
//						String className = getClass().getSimpleName();
//						String methodName = "applyZip";
//
//						verbose("[ %s.%s ] [ %s ] Size [ %s ] CRC [ %s ]\n",
//							className, methodName, inputName, inputLength, inputCRC);
//						verbose("[ %s.%s ] [ %s ] Compressed size [ %s ] Method [ %s ]\n",
//								className, methodName, inputName, inputCompressed, inputMethod);
//						verbose("[ %s.%s ] [ %s ] Created [ %s ] Accessed [ %s ] Modified [ %s ]\n",
//								className, methodName, inputName, inputCreation, inputAccess, inputModified);
//					}

					int intInputLength;
					if ( inputLength == -1L ) {
						intInputLength = -1;
					} else {
						intInputLength = FileUtils.verifyArray(0, inputLength);
					}

					InputStreamData outputData = selectedAction.apply(inputName, zipInputStream, intInputLength);

					recordTransform(selectedAction, inputName);

					// TODO: Should more of the entry details be transferred?

					ZipEntry outputEntry = new ZipEntry(inputName);
					zipOutputStream.putNextEntry(outputEntry); // throws IOException
					FileUtils.transfer(outputData.stream, zipOutputStream, buffer); // throws IOException 
					zipOutputStream.closeEntry(); // throws IOException					
				}

				prevName = inputName;
				inputName = null;
			}

		} catch ( IOException e ) {
			String message;
			if ( inputName != null ) { // Actively processing an entry.
				message = "Failure while processing [ " + inputName + " ] from [ " + inputPath + " ] to [ " + outputPath + " ]";
			} else if ( prevName != null ) { // Moving to a new entry but not the first entry.
				message = "Failure after processing [ " + prevName + " ] from [ " + inputPath + " ] to [ " + outputPath + " ]";
			} else { // Moving to the first entry.
				message = "Failed to process first entry of [ " + inputPath + " ] to [ " + outputPath + " ]";
			}
			throw new JakartaTransformException(message, e);
		}
	}

	// Byte base JAR conversion is not supported.

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength) throws JakartaTransformException {
		throw new UnsupportedOperationException();
	}
}
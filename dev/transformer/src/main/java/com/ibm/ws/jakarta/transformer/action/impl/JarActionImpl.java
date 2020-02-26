package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.ContainerChanges;
import com.ibm.ws.jakarta.transformer.action.JarAction;
import com.ibm.ws.jakarta.transformer.util.FileUtils;
import com.ibm.ws.jakarta.transformer.util.InputStreamData;

public class JarActionImpl extends ContainerActionImpl implements JarAction {

	public JarActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);
	}

	//

	public String getName() {
		return "Jar Action";
	}

	@Override
	public ActionType getActionType() {
		return ActionType.JAR;
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

	//

	@Override
	public String getAcceptExtension() {
		return ".jar";
	}

	//

	@Override
	public void apply(
		String inputPath, InputStream inputStream, long inputCount,
		OutputStream outputStream) throws JakartaTransformException {

		setResourceNames(inputPath, inputPath);

		// Use Zip streams instead of Jar streams.
		//
		// Jar streams automatically read and consume the manifest, which we don't want.

		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

		try {
			apply(inputPath, zipInputStream, zipOutputStream);
			// throws JakartaTransformException

		} finally {
			try {
				zipOutputStream.finish(); // throws IOException
			} catch ( IOException e ) {
				throw new JakartaTransformException("Failed to complete output [ " + inputPath + " ]", e);
			}
		}
	}

	protected void apply(
		String inputPath, ZipInputStream zipInputStream,
		ZipOutputStream zipOutputStream) throws JakartaTransformException {

		String prevName = null;
		String inputName = null;

		try {
			byte[] buffer = new byte[FileUtils.BUFFER_ADJUSTMENT];

			ZipEntry inputEntry;
			while ( (inputEntry = zipInputStream.getNextEntry()) != null ) {
				inputName = inputEntry.getName();
				long inputLength = inputEntry.getSize();

				verbose("[ %s.%s ] [ %s ] Size [ %s ]\n",
					getClass().getSimpleName(), "applyZip", inputName, inputLength);

				boolean selected = select(inputName);
				Action acceptedAction = acceptAction(inputName);

				if ( !selected || (acceptedAction == null) ) {
					if ( acceptedAction == null ) {
						recordUnaccepted(inputName);
					} else {
						recordUnselected(acceptedAction, !ContainerChanges.HAS_CHANGES, inputName);
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

					InputStreamData outputData =
						acceptedAction.apply(inputName, zipInputStream, intInputLength);

					recordTransform(acceptedAction, inputName);

					// TODO: Should more of the entry details be transferred?

					ZipEntry outputEntry = new ZipEntry( acceptedAction.getChanges().getOutputResourceName() );
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
				message = "Failure while processing [ " + inputName + " ] from [ " + inputPath + " ]";
			} else if ( prevName != null ) { // Moving to a new entry but not the first entry.
				message = "Failure after processing [ " + prevName + " ] from [ " + inputPath + " ]";
			} else { // Moving to the first entry.
				message = "Failed to process first entry of [ " + inputPath + " ]";
			}
			throw new JakartaTransformException(message, e);
		}
	}
}
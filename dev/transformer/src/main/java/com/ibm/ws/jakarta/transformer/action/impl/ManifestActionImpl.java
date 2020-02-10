package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.ManifestAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;
import com.ibm.ws.jakarta.transformer.util.ManifestWriter;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;

public class ManifestActionImpl extends ActionImpl implements ManifestAction {

	public static final boolean IS_MANIFEST = true;
	public static final boolean IS_FEATURE = !IS_MANIFEST;	

	public ManifestActionImpl(ActionImpl parent, boolean isManifest) {
		super(parent);
		this.isManifest = isManifest;
	}

	public ManifestActionImpl(
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		boolean isManifest,
		Map<String, String> versions, Map<String, BundleData> bundleUpdates) {

		super(includes, excludes, renames, versions, bundleUpdates);

		this.isManifest = isManifest;
	}

	public ManifestActionImpl(
		PrintStream logStream, boolean isTerse, boolean isVerbose,
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		boolean isManifest,
		Map<String, String> versions,
		Map<String, BundleData> bundleUpdates) {

		super(logStream, isTerse, isVerbose,
		      includes, excludes,
		      renames, versions, bundleUpdates);

		this.isManifest = isManifest;
	}

	public ManifestActionImpl(ActionImpl parent) {
		this(parent, IS_MANIFEST);
	}

	public ManifestActionImpl(Set<String> includes, Set<String> excludes, Map<String, String> renames) {
		this(includes, excludes, renames, IS_MANIFEST, null, null);
	}

	public ManifestActionImpl(
		PrintStream logStream, boolean isTerse, boolean isVerbose,
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		Map<String, String> versions, Map<String, BundleData> bundleUpdates) {

		this(logStream, isTerse, isVerbose,
			 includes, excludes,
			 renames, IS_MANIFEST, versions, bundleUpdates);
	}

	//

	public String getName() {
		return ( getIsManifest() ? "Manifest Action" : "Feature Action" );
	}

	//

	private final boolean isManifest;

	public boolean getIsManifest() {
		return isManifest;
	}

	public boolean getIsFeature() {
		return !isManifest;
	}

	//

	@Override
	protected ManifestChangesImpl newChanges() {
		return new ManifestChangesImpl();
	}

	@Override
	public ManifestChangesImpl getChanges() {
		return (ManifestChangesImpl) super.getChanges();
	}

	protected void addReplacement() {
		getChanges().addReplacement();
	}

	protected void addReplacements(int additions) {
		getChanges().addReplacements(additions);
	}

	//

	@Override
	public boolean accept(String resourceName) {
		int resourceLen = resourceName.length();

		int minLen;
		String reqEnd;
		if ( getIsManifest() ) {
			minLen = 11;
			reqEnd = "MANIFEST.MF";
		} else {
			minLen = 3;
			reqEnd = ".MF";
		}

		if ( resourceLen < minLen ) {
			return false;
		} else {
			return resourceName.regionMatches(true, resourceLen - minLen, reqEnd, 0, minLen);
		}
	}

	//

	@Override
	public ByteData apply(String initialName, byte[] initialBytes, int initialCount)
		throws JakartaTransformException {

		String className = getClass().getSimpleName();
		String methodName = "apply";

		verbose("[ %s.%s ]: [ %s ] Initial bytes [ %s ]\n", className, methodName, initialName, initialCount);

		clearChanges();
		setResourceNames(initialName, initialName);

		ByteData initialData = new ByteData(initialName, initialBytes, 0, initialCount);

		Manifest initialManifest;
		try {
			initialManifest = new Manifest( initialData.asStream() );
		} catch ( IOException e ) {
			error("Failed to parse manifest [ %s ]\n", e, initialName);
			return null;
		}

		Manifest finalManifest = new Manifest();

		transform(initialName, initialManifest, finalManifest);

		log("[ %s.%s ]: [ %s ] Replacements [ %s ]\n",
			getClass().getSimpleName(), "transform",
			initialName, getChanges().getReplacements());

		if ( !hasChanges() ) {
			verbose("[ %s.%s ]: [ %s ] Null transform", className, methodName, initialName);
			return null;
		}

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(initialCount);
		try {
			write(finalManifest, outputStream); // throws IOException
		} catch ( IOException e ) {
			error("Failed to write manifest [ %s ]\n", e, initialName);
			return null;
		}

		byte[] finalBytes = outputStream.toByteArray();
		verbose("[ %s.%s ]: [ %s ] Active transform; final bytes [ %s ]\n", className, methodName, initialName, finalBytes.length);

		return new ByteData(initialName, finalBytes); 
	}

	protected void transform(String inputName, Manifest initialManifest, Manifest finalManifest) {
		Attributes initialMainAttributes = initialManifest.getMainAttributes();
		Attributes finalMainAttributes = finalManifest.getMainAttributes();

		addReplacements( transformPackages(inputName, "main", initialMainAttributes, finalMainAttributes) );

		if ( transformBundleIdentity(inputName, initialMainAttributes, finalMainAttributes) ) {
			addReplacement();
		}

		Map<String, Attributes> initialEntries = initialManifest.getEntries();
		Map<String, Attributes> finalEntries = finalManifest.getEntries();

		for ( Map.Entry<String, Attributes> entry : initialEntries.entrySet() ) {
			String entryKey = entry.getKey();
			Attributes initialEntryAttributes = entry.getValue();

			Attributes finalAttributes = new Attributes( initialEntryAttributes.size() );
			finalEntries.put(entryKey, finalAttributes);

			addReplacements( transformPackages(inputName, entryKey, initialEntryAttributes, finalAttributes) );
		}
	}

	private static final Set<String> SELECT_ATTRIBUTES;

	static {
		Set<String> useNames = new HashSet<String>();
		useNames.add("DynamicImport-Package");
		useNames.add("Export-Package");
	    useNames.add("Import-Package");
	    SELECT_ATTRIBUTES = useNames;
	}

	protected boolean selectAttribute(String name) {
		return SELECT_ATTRIBUTES.contains(name);
	}

	protected int transformPackages(
		String inputName, String entryName,
		Attributes initialAttributes, Attributes finalAttributes) {

		verbose(
			"Transforming [ %s ]: [ %s ] Attributes [ %d ]\n",
			inputName, entryName, initialAttributes.size() );		

		int replacements = 0;

		for ( Map.Entry<Object, Object> entries : initialAttributes.entrySet() ) {
			Object untypedName = entries.getKey(); 
			String typedName = untypedName.toString();

			String initialValue = (String) entries.getValue();
			String finalValue = null;

			if ( selectAttribute(typedName) ) {
			   finalValue = replacePackages(typedName, initialValue);
			}

			if ( finalValue == null ) {
				finalValue = initialValue;
			} else {
				replacements++;
			}

			finalAttributes.put(untypedName, finalValue);
		}

		verbose(
			"Transformed [ %s ]: [ %s ] Attributes [ %d ] Replacements [ %d ]\n",
			inputName, entryName, finalAttributes.size(), replacements );

		return replacements;
	}

	protected void write(Manifest manifest, OutputStream outputStream) throws IOException {
		if ( getIsManifest() ) {
			writeAsManifest(manifest, outputStream); // throws IOException
		} else {
			writeAsFeature(manifest, outputStream); // throws IOException
		}
	}

	protected void writeAsManifest(Manifest manifest, OutputStream outputStream) throws IOException {
		//manifest.write(outputStream); // throws IOException
		ManifestWriter.write(manifest, outputStream);
	}

	// Copied and updated from:
	// https://github.com/OpenLiberty/open-liberty/blob/integration/
	// dev/wlp-featureTasks/src/com/ibm/ws/wlp/feature/tasks/FeatureBuilder.java
	
	@SuppressWarnings("unused")
	protected void writeAsFeature(Manifest manifest, OutputStream outputStream) throws IOException {
		PrintWriter writer = new PrintWriter(outputStream);

		StringBuilder builder = new StringBuilder();

		for ( Map.Entry<Object, Object> mainEntry : manifest.getMainAttributes().entrySet() ) {
			writer.append( mainEntry.getKey().toString() );
			writer.append(": ");

			String value = (String) mainEntry.getValue();
			if ( value.indexOf(',') == -1 ) {
				writer.append(value);

			} else {
				Parameters parms = OSGiHeader.parseHeader(value);

				boolean continuedLine = false;
				for ( Map.Entry<String, Attrs> parmEntry : parms.entrySet() ) {
					if ( continuedLine ) {
						writer.append(",\r\n ");
					}

					// bnd might have added ~ characters if there are duplicates in 
					// the source, so we should remove them before we output it so we
					// get back to the original intended content.

					String parmName = parmEntry.getKey();
					int index = parmName.indexOf('~');
					if ( index != -1 ) {
						parmName = parmName.substring(0, index);
					}
					writer.append(parmName);

					Attrs parmAttrs = parmEntry.getValue();
					for (Map.Entry<String, String> parmAttrEntry : parmAttrs.entrySet()) {
						String parmAttrName = parmAttrEntry.getKey();
						String parmAttrValue = quote( builder, parmAttrEntry.getValue() );

						writer.append("; ");
						writer.append(parmAttrName);
						writer.append('=');
						writer.append(parmAttrValue);
					}

					continuedLine = true;
				}
			}

			writer.append("\r\n");
		}

		writer.flush();
	}

	public String quote(StringBuilder sb, String value) {
		@SuppressWarnings("unused")
		boolean isClean = OSGiHeader.quote(sb, value);
		String quotedValue = sb.toString();
		sb.setLength(0);
		return quotedValue;
	}
	
	/**
	 * Checks the character before and after a match to verify that the match
	 * is NOT a subset of a larger package, and thus not really a match.
	 *
	 * @param text
	 * @param textLimit
	 * @param lastMatchEnd
	 * @param matchStart
	 * @param keyLen
	 * @return
	 */
	protected boolean isTrueMatch(String text, int textLimit, int matchStart, int keyLen ) {
		
		//  Verify the match is not part of a longer package name.				
        if ( matchStart > 0 ) {
            char charBeforeMatch = text.charAt(matchStart - 1);
            if ( Character.isJavaIdentifierPart(charBeforeMatch) || (charBeforeMatch == '.')) { 
                return false;
            }
        }
        int matchEnd = matchStart + keyLen;
        if ( textLimit > matchEnd ) {
            char charAfterMatch = text.charAt(matchEnd);
            if ( Character.isJavaIdentifierPart(charAfterMatch) || (charAfterMatch == '.') ) {
                return false;
            }
        }
        return true;
	}
	
	
	/**
	 * Replace all embedded packages of specified text with replacement
	 * packages.
	 *
	 * @param text Text embedding zero, one, or more package names.
	 *
	 * @return The text with all embedded package names replaced.  Null if no
	 *     replacements were performed.
	 */
	protected String replacePackages(String mainAttribute, String text) {

		// System.out.println("Initial text [ " + text + " ]");

		String initialText = text;

		for ( Map.Entry<String, String> renameEntry : getPackageRenames().entrySet() ) {
			String key = renameEntry.getKey();
			int keyLen = key.length();

			// System.out.println("Next target [ " + key + " ]");

			int textLimit = text.length() - keyLen;

			int lastMatchEnd = 0;
			while ( lastMatchEnd <= textLimit ) {
				int matchStart = text.indexOf(key, lastMatchEnd);
				if ( matchStart == -1 ) {
					break;
				}

				if ( !isTrueMatch(text, textLimit, matchStart, keyLen) ) {
					lastMatchEnd = matchStart + keyLen;
					continue;
				}

				String value = renameEntry.getValue();
				int valueLen = value.length();

				String head = text.substring(0, matchStart);				
				String tail = text.substring(matchStart + keyLen);
				
                int tailLenBeforeReplaceVersion = tail.length();			
				tail = replacePackageVersion(tail, getPackageVersions().get(value));				
				int tailLenAfterReplaceVersion = tail.length();

				text = head + value + tail;
				
				lastMatchEnd = matchStart + valueLen;
				
				// Replacing the key or the version can increase or decrease the text length.
				textLimit += (valueLen - keyLen);
				textLimit += (tailLenAfterReplaceVersion - tailLenBeforeReplaceVersion);

				// System.out.println("Next text [ " + text + " ]");
			}
		}

		if ( initialText == text) {
			// System.out.println("Final text is unchanged");
			return null;
		} else {
			// System.out.println("Final text [ " + text + " ]");
			return text;
		}
	}

	// DynamicImport-Package: com.ibm.websphere.monitor.meters;version="1.0.0
	//  ",com.ibm.websphere.monitor.jmx;version="1.0.0",com.ibm.ws.jsp.webcon
	//  tainerext,com.ibm.wsspi.request.probe.bci,com.ibm.wsspi.probeExtensio
	//  n,com.ibm.ws.webcontainer.monitor

	// Import-Package: javax.servlet;version="[2.6,3)",javax.servlet.annotati
	//  on;version="[2.6,3)",javax.servlet.descriptor;version="[2.6,3)",javax
	//  .servlet.http;version="[2.6,3)",com.ibm.wsspi.http;version="[2.0,3)",
	//  com.ibm.ws.javaee.dd;version="1.0",com.ibm.ws.javaee.dd.common;versio
	//  n="1.0",com.ibm.ws.javaee.dd.common.wsclient;version="1.0",com.ibm.ws
	//  .javaee.dd.web;version="1.0",com.ibm.ws.javaee.dd.web.common;version=
	//  "1.0",com.ibm.ws.util;version="[1.0,2)",com.ibm.wsspi.injectionengine
	//  ;version="[3.0,4)",com.ibm.ws.runtime.metadata;version="[1.1,2)"

	/**
	 * Answer package attribute text which has been updated with a new version range.
	 *
	 * Examples 
	 * 
	 * <quote>
	 * Import-Package: javax.servlet;version="[2.6,3)",javax.servlet.annotation;version="[2.6,3)"
	 * </quote>
	 * 
	 * <quote>
	 * DynamicImport-Package: com.ibm.websphere.monitor.meters;version="1.0.0",com.ibm.websphere.monitor.jmx;version="1.0.0"
	 * </quote>
	 * 
	 * The leading package name must be removed from the attribute text.  Other package
	 * names and attributes may be present.
	 * 
	 * Attribute text for different packages use commas as separators, except, commas inside
	 * quotation marks are not separators.  This is important because commas are present in
	 * version ranges.
	 *
	 * @param text Package attribute text.
	 * @param newVersion Replacement version values for the package attribute.
	 *                  
	 * @return String with version numbers of first package replaced by the newVersion.
	 */
	protected String replacePackageVersion(String text, String newVersion) {
		//verbose("replacePackageVersion: ( %s )\n",  text );

		String packageText = getPackageAttributeText(text);
		if ( packageText == null ) {
			return text;
		} else if ( packageText.isEmpty() ) {
			return text;
		}

		//verbose("replacePackageVersion: (packageText: %s )\n", packageText);

		final String VERSION = "version";
		final int VERSION_LEN = 7;
		final char QUOTE_MARK = '\"';

		int versionIndex = packageText.indexOf(VERSION);
		if ( versionIndex == -1 ) { 
			return text;  // nothing to replace
		}

		// The actual version numbers are after the "version" and the "=" and between quotation marks ("").
		// Ignore white space that occurs around the "=", but do not ignore white space between quotation marks.
		// Everything inside the "" is part of the version and will be replaced.
    	boolean foundEquals = false;
    	boolean foundQuotationMark = false; 
    	int versionBeginIndex = -1;
    	int versionEndIndex = -1;

		// skip to actual version number which is after "=".  Version begins inside double quotation marks 
        for (int i=versionIndex + VERSION_LEN; i < packageText.length(); i++) {
        	char ch = packageText.charAt(i);

        	// skip white space until we find equals sign
        	if ( !foundEquals ) {
        		if (ch == '=') {
        			foundEquals = true;
        			continue;
        		}

        		if ( Character.isWhitespace(ch)) {
        			continue;
        		}
        		error("Syntax error found non-white-space character before equals sign in version {%s}\n", packageText);
        		return text;   // Syntax error - returning original text
        	}

        	// Skip white space past the equals sign
        	if ( Character.isWhitespace(ch) ) {
    			// verbose("ch is \'%s\' and is whitespace.\n", ch);
    			continue;
    		}

        	// When we find the quotation marks past the equals sign, we are finished.
        	if ( !foundQuotationMark ) {
        		if ( ch == QUOTE_MARK ) {
        			versionBeginIndex = i+1;  // just past the 1st quotation mark

        			versionEndIndex = packageText.indexOf('\"', i+1);
        			if (versionEndIndex == -1) {
        				error("Syntax error, package version does not have closing quotation mark\n");
        				return text; // Syntax error - returning original text
        			}
        			versionEndIndex--; // just before the 2nd quotation mark

        			//verbose("versionBeginIndex = [%s]\n", versionBeginIndex);
            		//verbose("versionEndIndex = [%s]\n", versionEndIndex);
        			foundQuotationMark = true; // not necessary, just leave loop
        			break;
        		}

        		if ( Character.isWhitespace(ch) ) {
        			continue;
        		}

        		error("Syntax error found non-white-space character after equals sign  in version {%s}\n", packageText);
        		return text;   // Syntax error - returning original text
        	}
        }

    	//String oldVersion = packageText.substring(versionBeginIndex, versionEndIndex+1);
    	//verbose("old version[ %s ] new version[ %s]\n", oldVersion, newVersion);

    	String head = text.substring(0, versionBeginIndex);
    	String tail = text.substring(versionEndIndex+1);

    	String newText = head + newVersion + tail;
    	//verbose("Old [%s] New [%s]\n", text , newText);

		return newText;
	}

	/**
	 * 
	 * @param text  - A string containing package attribute text at the head of the string.
	 *         Assumptions: - The first package name has already been stripped from the embedding text.
	 *                      - Other package names and attributes may or may not follow.
	 *                      - Packages are separated by a comma.
	 *                      - If a comma is inside quotation marks, it is not a package delimiter.
	 * @return
	 */
	protected String getPackageAttributeText(String text) {
		//verbose("getPackageAttributeText ENTER[ text: %s]\n", text);

		if ( text == null ) {
			return null;
		}

		if ( !firstCharIsSemicolon(text) ) {
			return "";  // no package attributes
		}

		int commaIndex = text.indexOf(',');
		verbose("Comma index: [%d]\n", commaIndex);
		// If there is no comma, then the whole text is the packageAttributeText
		if ( commaIndex == -1 ) {
			return text;
		}

		// packageText is beginning of text up to and including comma.
		// Need to test whether the comma is within quotes - thus not the true end of the packageText.
		// If an odd number of quotes are found, then the comma is in quotes and we need to find the next comma.
		String packageText = text.substring(0, commaIndex+1);   
		verbose("packageText [ %s ]\n", packageText);
		boolean quotesAreEven = hasEvenNumberOfOccurrencesOfChar(packageText, '\"');

		if ( !quotesAreEven ) {
			commaIndex = text.indexOf(',', packageText.length());
			if ( commaIndex == -1 ) {
				packageText = text;  // No trailing comma indicates embedding text is the package text.
			} else {
			   verbose("Updating packageText [ %s ]\n", packageText);
			   verbose("commaIndex is [ %d ]\n", commaIndex);
			   packageText = text.substring(0, commaIndex+1);
			   verbose("new      packageText [ %s ]\n", packageText);
			}
		}

		verbose("getPackageAttributeText returning: [ %s ]\n", packageText);
		return packageText;
	}

	/**
	 * Tell if the first non-white space character of the parameter is a semi-colon.
	 */
	protected boolean firstCharIsSemicolon(String s) {
		for ( int i=0; i < s.length(); i++ ) {
			if ( Character.isWhitespace(s.charAt(i)) ) {
				continue;
			}
			if ( s.charAt(i) == ';' ) {
				return true;
			}
			return false;
		}
		return false;
	}

	private boolean hasEvenNumberOfOccurrencesOfChar(String testString, char testChar) {
		long occurrences = testString.chars().filter(ch -> ch == '\"').count();
		return ((occurrences % 2 ) == 0);
	}

	//

	public static final String SYMBOLIC_NAME_PROPERTY_NAME = "Bundle-SymbolicName";
	public static final String VERSION_PROPERTY_NAME = "Bundle-Version";
	public static final String NAME_PROPERTY_NAME = "Bundle-Name";
	public static final String DESCRIPTION_PROPERTY_NAME = "Bundle-Description";

	public boolean transformBundleIdentity(
		String inputName,
		Attributes initialMainAttributes,
		Attributes finalMainAttributes) {

		String symbolicName = initialMainAttributes.getValue(SYMBOLIC_NAME_PROPERTY_NAME);
		if ( symbolicName == null ) {
			return false;
		}

		BundleData bundleUpdate = getBundleUpdate(symbolicName);
		if ( bundleUpdate == null ) {
			return false;
		}

		finalMainAttributes.putValue(
			SYMBOLIC_NAME_PROPERTY_NAME,
			bundleUpdate.getSymbolicName());
		finalMainAttributes.putValue(
			VERSION_PROPERTY_NAME,
			bundleUpdate.getVersion());
		finalMainAttributes.putValue(
			NAME_PROPERTY_NAME,
			bundleUpdate.updateName(initialMainAttributes.getValue(NAME_PROPERTY_NAME)));
		finalMainAttributes.putValue(
			DESCRIPTION_PROPERTY_NAME,
			bundleUpdate.updateDescription(initialMainAttributes.getValue(DESCRIPTION_PROPERTY_NAME)));

		return true;
	}
}

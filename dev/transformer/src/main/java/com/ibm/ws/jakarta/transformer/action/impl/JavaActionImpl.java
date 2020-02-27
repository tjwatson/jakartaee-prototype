package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.JavaAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;

public class JavaActionImpl extends ActionImpl implements JavaAction {	

	public JavaActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

        super(logger, buffer, selectionRule, signatureRule);
	}

	//

	@Override
	public String getName() {
		return ( "Java Action" );
	}

	@Override
	public ActionType getActionType() {
		return ( ActionType.JAVA );
	}

	//

	@Override
	protected JavaChangesImpl newChanges() {
		return new JavaChangesImpl();
	}

	@Override
	public JavaChangesImpl getChanges() {
		return (JavaChangesImpl) super.getChanges();
	}

	protected void addReplacement() {
		getChanges().addReplacement();
	}

	protected void addReplacements(int additions) {
		getChanges().addReplacements(additions);
	}

	//

	@Override
	public String getAcceptExtension() {
		return ".java";
	}

	//

	/**
     * Replace all embedded packages of specified text with replacement
     * packages.
     *
     * @param text Text embedding zero, one, or more package names.
     *
     * @return The text with all embedded package names replaced.  Null if no
     *     replacements were performed.
     */
    protected String replacePackages(String text) {
        //System.out.println("replacePackages: Initial text [ " + text + " ]");

        String initialText = text;

        for ( Map.Entry<String, String> renameEntry : getPackageRenames().entrySet() ) {
            String key = renameEntry.getKey();
            int keyLen = key.length();

            //System.out.println("replacePackages: Next target [ " + key + " ]");

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

//                int tailLenBeforeReplaceVersion = tail.length();            
//                tail = replacePackageVersion(tail, getPackageVersions().get(value));
//                int tailLenAfterReplaceVersion = tail.length();

                text = head + value + tail;

                lastMatchEnd = matchStart + valueLen;

                // Replacing the key or the version can increase or decrease the text length.
                textLimit += (valueLen - keyLen);
//                textLimit += (tailLenAfterReplaceVersion - tailLenBeforeReplaceVersion);

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

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength) 
		throws JakartaTransformException {

		clearChanges();

		String outputName = inputName; 
		// String outputName = renameInput(inputName); // TODO
		if ( outputName == null ) {
			outputName = inputName;
		} else {
			log("Class name [ %s ]\n          -> [ %s ]\n", inputName, outputName);
		}
		setResourceNames(inputName, outputName);

		InputStream inputStream = new ByteArrayInputStream(inputBytes);
		InputStreamReader inputReader;
		try {
			inputReader = new InputStreamReader(inputStream, "UTF-8");
		} catch ( UnsupportedEncodingException e ) {
			error("Strange: UTF-8 is an unrecognized encoding for reading [ %s ]\n", e, inputName);
			return null;
		}

		BufferedReader reader = new BufferedReader(inputReader);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(inputBytes.length);
		OutputStreamWriter outputWriter;
		try {
			outputWriter = new OutputStreamWriter(outputStream, "UTF-8");
		} catch ( UnsupportedEncodingException e ) {
			error("Strange: UTF-8 is an unrecognized encoding for writing [ %s ]\n", e, inputName);
			return null;
		}

		BufferedWriter writer = new BufferedWriter(outputWriter);

		try {
			transform(reader, writer); // throws IOException
		} catch ( IOException e ) {
			error("Failed to transform [ %s ]\n", e, inputName);
			return null;
		}

		try {
			writer.flush(); // throws
		} catch ( IOException e ) {
			error("Failed to flush [ %s ]\n", e, inputName);
			return null;
		}

		if ( !hasNonResourceNameChanges() ) {
			return null;
		}

		byte[] outputBytes = outputStream.toByteArray();
		return new ByteData(inputName, outputBytes, 0, outputBytes.length);
	}

	protected void transform(BufferedReader reader, BufferedWriter writer)
		throws IOException {

		String inputLine;
		while ( (inputLine = reader.readLine()) != null ) {
			String outputLine = replacePackages(inputLine);
			if ( outputLine == null ) {
				outputLine = inputLine;
			} else {
				addReplacement();
			}
			writer.write(outputLine);
			writer.write('\n');
		}
    }

	// TODO: Copied from ServiceConfigActionImpl; need to update
	//       to work for paths.

	protected String renameInput(String inputName) {
		String inputPrefix;
		String classQualifiedName;

		int lastSlash = inputName.lastIndexOf('/');
		if ( lastSlash == -1 ) {
			inputPrefix = null;
			classQualifiedName = inputName;
		} else {
			inputPrefix = inputName.substring(0, lastSlash + 1);
			classQualifiedName = inputName.substring(lastSlash + 1);
		}

		int classStart = classQualifiedName.lastIndexOf('.');
		if ( classStart == -1 ) {
			return null;
		}

		String packageName = classQualifiedName.substring(0, classStart);
		if ( packageName.isEmpty() ) {
			return null;
		}

		// 'className' includes a leading '.'
		String className = classQualifiedName.substring(classStart);

		String outputName = replacePackage(packageName);
		if ( outputName == null ) {
			return null;
		}

		if ( inputPrefix == null ) {
			return outputName + className;
		} else {
			return inputPrefix + outputName + className;
		}
	}
}

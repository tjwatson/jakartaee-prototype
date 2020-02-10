package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.JakartaTransformProperties;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.util.ByteData;
import com.ibm.ws.jakarta.transformer.util.FileUtils;
import com.ibm.ws.jakarta.transformer.util.InputStreamData;

import aQute.bnd.signatures.ArrayTypeSignature;
import aQute.bnd.signatures.BaseType;
import aQute.bnd.signatures.ClassSignature;
import aQute.bnd.signatures.ClassTypeSignature;
import aQute.bnd.signatures.FieldSignature;
import aQute.bnd.signatures.JavaTypeSignature;
import aQute.bnd.signatures.MethodSignature;
import aQute.bnd.signatures.ReferenceTypeSignature;
import aQute.bnd.signatures.Result;
import aQute.bnd.signatures.SimpleClassTypeSignature;
import aQute.bnd.signatures.ThrowsSignature;
import aQute.bnd.signatures.TypeArgument;
import aQute.bnd.signatures.TypeParameter;
import aQute.bnd.signatures.TypeVariableSignature;

public abstract class ActionImpl implements Action {

	//

	public ActionImpl(ActionImpl parent) {
		ActionImpl useRoot = parent.getRoot(); 
		this.root = ( (useRoot == null) ? parent : useRoot );
		this.parent = parent;

		this.logStream = null;
		this.isTerse = false;
		this.isVerbose = false;

		//

		this.included = null;
		this.includedExact = null;
		this.includedHead = null;
		this.includedTail = null;
		this.includedAny = null;

		this.excluded = null;
		this.excludedExact = null;
		this.excludedHead = null;
		this.excludedTail = null;
		this.excludedAny = null;

		this.packageRenames = null;
		this.binaryPackageRenames = null;

		this.packageVersions = null;
		this.bundleUpdates = null;

		//

		this.unchangedBinaryTypes = null;
		this.changedBinaryTypes = null;

		this.unchangedSignatures = null;
		this.changedSignatures = null;

		this.unchangedDescriptors = null;
		this.changedDescriptors = null;

		//

		this.changes = newChanges();
	}

	//

	protected final ActionImpl root;

	public ActionImpl getRoot() {
		return root;
	}

	private final ActionImpl parent;

	public ActionImpl getParent() {
		return parent;
	}

	//

	public static final PrintStream NULL_STREAM = null;

	public static final boolean IS_TERSE = true;
	public static final boolean IS_VERBOSE = true;

	public ActionImpl(
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		Map<String, String> versions,
		Map<String, BundleData> bundleUpdates) {
		this(NULL_STREAM, !IS_TERSE, !IS_VERBOSE,
			 includes, excludes, renames, versions, bundleUpdates);
	}

	public ActionImpl(
		PrintStream logStream, boolean isTerse, boolean isVerbose,
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		Map<String, String> versions, Map<String, BundleData> bundleUpdates) {

		this.root = null;
		this.parent = null;

		this.logStream = logStream;
		this.isTerse = isTerse;
		this.isVerbose = isVerbose;

		this.included = new HashSet<String>(includes);
		this.includedExact = new HashSet<String>();
		this.includedHead = new HashSet<String>();
		this.includedTail = new HashSet<String>();
		this.includedAny = new HashSet<String>();

		JakartaTransformProperties.processSelections(
			this.included,
			this.includedExact, this.includedHead, this.includedTail, this.includedAny );

		this.excluded = new HashSet<String>(excludes);
		this.excludedExact = new HashSet<String>();
		this.excludedHead = new HashSet<String>();
		this.excludedTail = new HashSet<String>();
		this.excludedAny = new HashSet<String>();

		JakartaTransformProperties.processSelections(
			this.excluded,
			this.excludedExact, this.excludedHead, this.excludedTail, this.excludedAny );

		Map<String, String> useRenames = new HashMap<String, String>( renames.size() );
		Map<String, String> useBinaryRenames = new HashMap<String, String>( renames.size() );

		for ( Map.Entry<String, String> renameEntry : renames.entrySet() ) {
			// System.out.println("Binary conversion from [ " + renameEntry.getKey() + " ] to [ " + renameEntry.getValue() + " ]");
			String initialName = renameEntry.getKey();
			String finalName = renameEntry.getValue();

			useRenames.put(initialName, finalName);

			String initialBinaryName = initialName.replace('.',  '/');
			String finalBinaryName = finalName.replace('.',  '/');

			useBinaryRenames.put(initialBinaryName, finalBinaryName);
		}
		this.packageRenames = useRenames;
		this.binaryPackageRenames = useBinaryRenames;

		Map<String, String> useVersions;
		if (versions != null ) {
			useVersions = new HashMap<String, String>(versions);
		} else {
		    useVersions = Collections.emptyMap();
        }
		this.packageVersions = useVersions;

		Map<String, BundleData> useBundleUpdates;
		if ( bundleUpdates != null ) {
			useBundleUpdates = new HashMap<String, BundleData>(bundleUpdates);
		} else {
			useBundleUpdates = Collections.emptyMap();
		}
		this.bundleUpdates = useBundleUpdates;
		
		this.unchangedBinaryTypes = new HashSet<>();
		this.changedBinaryTypes = new HashMap<>();

		this.unchangedSignatures = new HashSet<>();
		this.changedSignatures = new HashMap<>();

		this.unchangedDescriptors = new HashSet<>();
		this.changedDescriptors = new HashMap<>();

		this.changes = newChanges();
	}

	//

	private final PrintStream logStream;
	private final boolean isTerse;
	private final boolean isVerbose;

	public PrintStream getLogStream() {
		return ( (root != null) ? root.getLogStream() : logStream );
	}

	public boolean getIsTerse() {
		return ( (root != null) ? root.getIsTerse() : isTerse );
	}

	public boolean getIsVerbose() {
		return ( (root != null) ? root.getIsVerbose() : isVerbose );
	}

	public void log(String text, Object... parms) {
		if ( root != null ) {
			root.log(text, parms);
		} else {
			if ( (logStream != null) && !isTerse ) {
				if ( parms.length == 0 ) {
					logStream.println(text);
				} else {
					logStream.printf(text, parms);
				}
			}
		}
	}

	protected void verbose(String text, Object... parms) {
		if ( root != null ) {
			root.verbose(text, parms);
		} else {
			if ( (logStream != null) && isVerbose ) {
				if ( parms.length == 0 ) {
					logStream.print(text);
				} else {
					logStream.printf(text, parms);
				}
			}
		}
	}

    protected void error(String message, Object... parms) {
    	if ( root != null ) {
    		root.error(message, parms);
    	} else {
    		if ( logStream != null ) {
    			if ( parms.length == 0 ) {
    				logStream.print("ERROR: " + message);
    			} else {
    				logStream.printf("ERROR: " + message, parms);
    			}
    		}
    	}
    }

    protected void error(String message, Throwable th, Object... parms) {
    	if ( root != null ) {
    		root.error(message, th, parms);
    	} else {
    		error(message, parms);
    		th.printStackTrace( getLogStream() );
    	}
    }

	//

	protected abstract ChangesImpl newChanges();

	protected final ChangesImpl changes;

	@Override
	public ChangesImpl getChanges() {
		return changes;
	}

	@Override
	public boolean hasChanges() {
		return getChanges().hasChanges();
	}
	
	protected void clearChanges() {
		getChanges().clearChanges();
	}

	protected void setResourceNames(String inputResourceName, String outputResourceName) {
		ChangesImpl useChanges = getChanges();
		useChanges.setInputResourceName(inputResourceName);
		useChanges.setOutputResourceName(outputResourceName);
	}

	//

	@Override
	public String asClassName(String resourceName) {
		return Action.resourceNameToClassName(resourceName);
	}

	@Override
	public String asResourceName(String className) {
		return Action.classNameToResourceName(className);
	}
	
	@Override
	public String asBinaryTypeName(String className) {
		return Action.classNameToBinaryTypeName(className);
	}

	private final Set<String> included;
	private final Set<String> includedExact;
	private final Set<String> includedHead;
	private final Set<String> includedTail;
	private final Set<String> includedAny;
	
	private final Set<String> excluded;
	private final Set<String> excludedExact;
	private final Set<String> excludedHead;
	private final Set<String> excludedTail;	
	private final Set<String> excludedAny;	

	@Override
	public boolean select(String resourceName) {
		if ( root != null ) {
			return root.select(resourceName);

		} else {
			boolean isIncluded = selectIncluded(resourceName);
			boolean isExcluded = rejectExcluded(resourceName);

			return ( isIncluded && !isExcluded );
		}
	}

	public boolean selectIncluded(String resourceName) {
		if ( included.isEmpty() ) {
			verbose("Include [ %s ]: %s\n", resourceName, "No includes");
			return true;

		} else if ( includedExact.contains(resourceName) ) {
			verbose("Include [ %s ]: %s\n", resourceName, "Exact include");
			return true;

		} else {
			for ( String tail : includedHead ) {
				if ( resourceName.endsWith(tail) ) {
					verbose("Include [ %s ]: %s (%s)\n", resourceName, "Match tail", tail);
					return true;
				}
			}
			for ( String head : includedTail ) {
				if ( resourceName.startsWith(head) ) {
					verbose("Include [ %s ]: %s (%s)\n", resourceName, "Match head", head);
					return true;
				}
			}
			for ( String middle : includedAny ) {
				if ( resourceName.contains(middle) ) {
					verbose("Include [ %s ]: %s (%s)\n", resourceName, "Match middle", middle);
					return true;
				}
			}

			verbose("Do not include [ %s ]\n", resourceName);
			return false;
		}
	}

	public boolean rejectExcluded(String resourceName ) {
		if ( excluded.isEmpty() ) {
			verbose("Do not exclude[ %s ]: %s\n", resourceName, "No excludes");
			return false;

		} else if ( excludedExact.contains(resourceName) ) {
			verbose("Exclude [ %s ]: %s\n", resourceName, "Exact exclude");
			return true;

		} else {
			for ( String tail : excludedHead ) {
				if ( resourceName.endsWith(tail) ) {
					verbose("Exclude[ %s ]: %s (%s)\n", resourceName, "Match tail", tail);
					return true;
				}
			}
			for ( String head : excludedTail ) {
				if ( resourceName.startsWith(head) ) {
					verbose("Exclude[ %s ]: %s (%s)\n", resourceName, "Match head", head);
					return true;
				}
			}
			for ( String middle : excludedAny ) {
				if ( resourceName.contains(middle) ) {
					verbose("Exclude[ %s ]: %s (%s)\n", resourceName, "Match middle", middle);
					return true;
				}
			}
			
			verbose("Do not exclude[ %s ]\n", resourceName);
			return false;
		}
	}

	//

	protected final Map<String, String> packageRenames;
	protected final Map<String, String> binaryPackageRenames;

	protected Map<String, String> getPackageRenames() {
		return packageRenames;
	}

	protected final Map<String, String> packageVersions;
	
	protected Map<String, String> getPackageVersions() {
		return packageVersions;
	}

	/**
	 * Replace a single package according to the package rename rules.
	 * 
	 * Package names must match exactly.
	 *
	 * @param initialName The package name which is to be replaced.
	 *
	 * @return The replacement for the initial package name.  Null if no
	 *     replacement is available.
	 */
	protected String replacePackage(String initialName) {
		if ( root != null ) {
			return root.replacePackage(initialName);
		} else {
			return packageRenames.getOrDefault(initialName, null);
		}
	}

	/**
	 * Replace a single package according to the package rename rules.
	 * The package name has '/' separators, not '.' separators.
	 *
	 * Package names must match exactly.
	 *
	 * @param initialName The package name which is to be replaced.
	 *
	 * @return The replacement for the initial package name.  Null if no
	 *     replacement is available.
	 */
	protected String replaceBinaryPackage(String initialName) {
		if ( root != null ) {
			return root.replaceBinaryPackage(initialName);
		} else {
			String finalName = binaryPackageRenames.getOrDefault(initialName, null);
			// System.out.println("Initial binary [ " + initialName + " ] Final [ " + finalName + " ]");
			return finalName;
		}
	}

	/**
	 * Replace all embedded packages of specified text with replacement
	 * packages.
	 *
	 * @param embeddingText Text embedding zero, one, or more package names.
	 *
	 * @return The text with all embedded package names replaced.  Null if no
	 *     replacements were performed.
	 */
	protected String replaceEmbeddedPackages(String embeddingText) {
		if ( root != null ) {
			return root.replaceEmbeddedPackages(embeddingText);

		} else {
			// System.out.println("Initial text [ " + embeddingText + " ]");

			String initialText = embeddingText;

			for ( Map.Entry<String, String> renameEntry : packageRenames.entrySet() ) {
				String key = renameEntry.getKey();
				int keyLen = key.length();

				// System.out.println("Next target [ " + key + " ]");

				int textLimit = embeddingText.length() - keyLen;

				int lastMatchEnd = 0;
				while ( lastMatchEnd <= textLimit ) {
					int nextMatchStart = embeddingText.indexOf(key, lastMatchEnd);
					if ( nextMatchStart == -1 ) {
						break;
					}

					String value = renameEntry.getValue();
					int valueLen = value.length();

					String head = embeddingText.substring(0, nextMatchStart);
					String tail = embeddingText.substring(nextMatchStart + keyLen);
					embeddingText = head + value + tail;

					lastMatchEnd = nextMatchStart + valueLen;
					textLimit += (valueLen - keyLen);

					// System.out.println("Next text [ " + embeddingText + " ]");
				}
			}

			if ( initialText == embeddingText) {
				// System.out.println("Final text is unchanged");
				return null;
			} else {
				// System.out.println("Final text [ " + embeddingText + " ]");
				return embeddingText;
			}
		}
	}


    //

	private final Map<String, String> changedBinaryTypes;
	private final Set<String> unchangedBinaryTypes;

	/**
	 * Modify a fully qualified type name according to the package rename table.
	 * Answer either the transformed type name, or, if the type name was not changed,
	 * a wrapped null.
	 * 
	 * @param inputName A fully qualified type name which is to be transformed.
	 *
	 * @return The transformed type name, or a wrapped null if no changed was made.
	 */
	protected String transformBinaryType(String inputName) {
		if ( root != null ) {
			return root.transformBinaryType(inputName);
		}

		// System.out.println("Input type [ " + inputName + " ]");

		if ( unchangedBinaryTypes.contains(inputName) ) {
			// System.out.println("Unchanged (Prior)");
			return null;
		}

		String outputName = changedBinaryTypes.get(inputName);
		if ( outputName != null ) {
			// System.out.println("Change to [ " + outputName + " ] (Prior)");
			return outputName;
		}

		char c = inputName.charAt(0);
		if ( (c == '[') || ((c == 'L') && (inputName.charAt(inputName.length() - 1) == ';')) ) {
			JavaTypeSignature inputSignature = JavaTypeSignature.of( inputName.replace('$', '.') );
			JavaTypeSignature outputSignature = transform(inputSignature);
			if ( outputSignature != null ) {
				outputName = outputSignature.toString().replace('.', '$');
			} else {
				// Leave outputName null.
			}

		} else {
			int lastSlashOffset = inputName.lastIndexOf('/');
			if ( lastSlashOffset != -1 ) {
				String inputPackage = inputName.substring(0, lastSlashOffset);
				// System.out.println("Input package [ " + inputPackage + " ]");
				String outputPackage = replaceBinaryPackage(inputPackage);
				if ( outputPackage != null ) {
					// System.out.println("Output package [ " + outputPackage + " ]");
					outputName = outputPackage + inputName.substring(lastSlashOffset);
				} else {
					// Leave outputName null.
				}
			} else {
				// Leave outputName with null;
			}
		}

		if ( outputName == null ) {
			unchangedBinaryTypes.add(inputName);
			// System.out.println("Unchanged");
		} else {
			changedBinaryTypes.put(inputName, outputName);
			// System.out.println("Change to [ " + outputName + " ]");
		}

		return outputName;
	}

	/**
	 * Cache of transformed signatures.
	 * 
	 * A single unified mapping is used, even through there are three different
	 * types of signatures.  The different types of signatures each has its
	 * own syntax, meaning, there are not equal values across signature types.
	 */
	
	private final Set<String> unchangedSignatures;
	private final Map<String, String> changedSignatures;

	protected static enum SignatureType {
		CLASS, FIELD, METHOD
	}

	/**
	 * Transform a class, field, or method signature.
	 * 
	 * Answer a wrapped null if the signature is not changed by the transformation
	 * rules.
	 *
	 * @param input The signature value which is to be transformed.
	 * @param signatureType The type of the signature value.
	 *
	 * @return The transformed signature value.  A wrapped null if no change
	 *     was made to the value.
	 */
	protected String transform(String input, SignatureType signatureType) {
		if ( root != null ) {
			return root.transform(input, signatureType);
		}

		if ( unchangedSignatures.contains(input) ) {
			return null;
		}

		String output = changedSignatures.get(input);
		if ( output != null ) {
			return output;
		}

		if ( signatureType == SignatureType.CLASS ) {
			ClassSignature inputSignature = ClassSignature.of(input);
			ClassSignature outputSignature = transform(inputSignature);
			if ( outputSignature != null ) {
				output = outputSignature.toString();
			} else {
				// leave output null;
			}

		} else if ( signatureType == SignatureType.FIELD ) {
			FieldSignature inputSignature = FieldSignature.of(input);
			FieldSignature outputSignature = transform(inputSignature);
			if ( outputSignature != null ) {
				output = outputSignature.toString();
			} else {
				// leave output null;
			}

		} else if ( signatureType == SignatureType.METHOD ) {
			MethodSignature inputSignature = MethodSignature.of(input);
			MethodSignature outputSignature = transform(inputSignature);
			if ( outputSignature != null ) {
				output = outputSignature.toString();
			} else {
				// leave output null
			}

		} else {
			throw new IllegalArgumentException(
				"Signature [ " + input + " ] uses unknown type [ " + signatureType + " ]");
		}

		if ( output == null ) {
			unchangedSignatures.add(input);
		} else {
			changedSignatures.put(input, output);
		}

		return output;
	}

	protected ClassSignature transform(ClassSignature classSignature) {
		TypeParameter[] inputTypes = classSignature.typeParameters;
		TypeParameter[] outputTypes = null;

		for ( int parmNo = 0; parmNo < inputTypes.length; parmNo++ ) {
			TypeParameter inputType = inputTypes[parmNo];
			TypeParameter outputType = transform(inputType);

			if ( outputType != null ) {
				if ( outputTypes == null ) {
					outputTypes = inputTypes.clone();
				}
				outputTypes[parmNo] = outputType;
			}
		}

		ClassTypeSignature inputSuperClass = classSignature.superClass;
		ClassTypeSignature outputSuperClass = transform(inputSuperClass);

		ClassTypeSignature[] inputInterfaces = classSignature.superInterfaces;
		ClassTypeSignature[] outputInterfaces = null;
		
		for ( int interfaceNo = 0; interfaceNo < inputInterfaces.length; interfaceNo++ ) {
			ClassTypeSignature inputInterface = inputInterfaces[interfaceNo];
			ClassTypeSignature outputInterface = transform(inputInterface);

			if ( outputInterface != null ) {
				if ( outputInterfaces == null ) {
					outputInterfaces = inputInterfaces.clone();
				}
				outputInterfaces[interfaceNo] = outputInterface;
			}
		}

		if ( (outputTypes == null) && (outputSuperClass == null) && (outputInterfaces == null) ) {
			return null;
		} else {
			return new ClassSignature(
				( (outputTypes == null) ? inputTypes : outputTypes ),
				( (outputSuperClass == null) ? inputSuperClass : outputSuperClass ),
				( (outputInterfaces == null) ? inputInterfaces : outputInterfaces) );
		}
	}

	protected FieldSignature transform(FieldSignature fieldSignature) {
		ReferenceTypeSignature inputType = fieldSignature.type;
		ReferenceTypeSignature outputType = transform(inputType);

		if ( outputType == null ) {
			return null;
		} else {
			return new FieldSignature(outputType);
		}
	}

	protected MethodSignature transform(MethodSignature methodSignature) {
		TypeParameter[] inputTypeParms = methodSignature.typeParameters;
		TypeParameter[] outputTypeParms = null;

		for ( int parmNo = 0; parmNo < inputTypeParms.length; parmNo++ ) {
			TypeParameter inputTypeParm = inputTypeParms[parmNo];
			TypeParameter outputTypeParm = transform(inputTypeParm);
			if ( outputTypeParm != null ) {
				if ( outputTypeParms == null ) {
					outputTypeParms = inputTypeParms.clone();
				}
				outputTypeParms[parmNo] = outputTypeParm;
			}
		}

		JavaTypeSignature[] inputParmTypes = methodSignature.parameterTypes;
		JavaTypeSignature[] outputParmTypes = null;

		for ( int parmNo = 0; parmNo < inputParmTypes.length; parmNo++ ) {
			JavaTypeSignature inputParmType = inputParmTypes[parmNo];
			JavaTypeSignature outputParmType = transform(inputParmType);
			if ( outputParmType != null ) {
				if ( outputParmTypes == null ) {
					outputParmTypes = inputParmTypes.clone();
				}
				outputParmTypes[parmNo] = outputParmType;
			}
		}

		Result inputResult = methodSignature.resultType;
		Result outputResult = transform(inputResult);

		ThrowsSignature[] inputThrows = methodSignature.throwTypes;
		ThrowsSignature[] outputThrows = null;
		
		for ( int throwNo = 0; throwNo < inputThrows.length; throwNo++ ) {
			ThrowsSignature inputThrow = inputThrows[throwNo];
			ThrowsSignature outputThrow = transform(inputThrow);
			if ( outputThrow != null ) {
				if ( outputThrows == null ) {
					outputThrows = inputThrows.clone();
				}
				outputThrows[throwNo] = outputThrow;
			}
		}

		if ( (outputTypeParms == null) &&
			 (outputParmTypes == null) &&
			 (outputResult == null) &&
			 (outputThrows == null) ) {
			return null;

		} else {
			return new MethodSignature(
				( (outputTypeParms == null) ? inputTypeParms : outputTypeParms ),
				( (outputParmTypes == null) ? inputParmTypes : outputParmTypes ),
				( (outputResult == null) ? inputResult : outputResult ),
				( (outputThrows == null) ? inputThrows : outputThrows ) );
		}
	}

	protected Result transform(Result type) {
		if ( type instanceof JavaTypeSignature ) {
			return transform((JavaTypeSignature) type);
		} else {
			return null;
		}
	}

	protected ThrowsSignature transform(ThrowsSignature type) {
		if ( type instanceof ClassTypeSignature ) {
			return transform((ClassTypeSignature) type);
		} else {
			return null;
		}
	}
	
	protected ArrayTypeSignature transform(ArrayTypeSignature inputType) {
		JavaTypeSignature inputComponent = inputType.component;
		int componentDepth = 1;
		while ( inputComponent instanceof ArrayTypeSignature ) {
			componentDepth++;
			inputComponent = ((ArrayTypeSignature) inputComponent).component;
		}
		if ( (inputComponent instanceof BaseType) || (inputComponent instanceof TypeVariableSignature) ) {
			return null;
		}

		JavaTypeSignature outputComponent = transform((ClassTypeSignature) inputComponent);
		if ( outputComponent == null ) {
			return null;
		}

		ArrayTypeSignature outputType = new ArrayTypeSignature(outputComponent);
		while ( --componentDepth > 0 ) {
			outputType = new ArrayTypeSignature(outputType);
		}
		return outputType;
	}

	protected TypeParameter transform(TypeParameter inputTypeParameter) {
		ReferenceTypeSignature inputClassBound = inputTypeParameter.classBound;
		ReferenceTypeSignature outputClassBound = transform(inputClassBound);

		ReferenceTypeSignature[] inputBounds = inputTypeParameter.interfaceBounds;
		ReferenceTypeSignature[] outputBounds = null;

		for ( int boundNo = 0; boundNo < inputBounds.length; boundNo++ ) {
			ReferenceTypeSignature inputBound = inputBounds[boundNo];
			ReferenceTypeSignature outputBound = transform(inputBound);
			if ( outputBound != null ) {
				if ( outputBounds == null ) {
					outputBounds = inputBounds.clone();
				}
				outputBounds[boundNo] = outputBound;
			}
		}

		if ( (outputClassBound == null) && (outputBounds == null) ) {
			return null;
		} else {
			return new TypeParameter(
				inputTypeParameter.identifier,
				( (outputClassBound == null) ? inputClassBound : outputClassBound ),
				( (outputBounds == null) ? inputBounds : outputBounds ) );
		}
	}

	protected ClassTypeSignature transform(ClassTypeSignature inputType) {
		String inputPackageSpecifier = inputType.packageSpecifier;
		String outputPackageSpecifier = null;

		int length = inputPackageSpecifier.length();
		if ( length > 0 ) {
			String inputBinaryPackage = inputPackageSpecifier.substring(0, length - 1);
			String outputBinaryPackage = replaceBinaryPackage(inputBinaryPackage);
			if ( outputBinaryPackage != null ) {
				outputPackageSpecifier = outputBinaryPackage + '/';
			}
		}

		SimpleClassTypeSignature inputClassType = inputType.classType;		
		SimpleClassTypeSignature outputClassType = transform(inputClassType);

		SimpleClassTypeSignature[] inputInnerTypes = inputType.innerTypes;
		SimpleClassTypeSignature[] outputInnerTypes = null;

		for ( int typeNo = 0; typeNo < inputInnerTypes.length; typeNo++ ) {
			SimpleClassTypeSignature inputInnerType = inputInnerTypes[typeNo]; 
			SimpleClassTypeSignature outputInnerType = transform(inputInnerType);
			if ( outputInnerType != null ) {
				if ( outputInnerTypes == null ) {
					outputInnerTypes = inputInnerTypes.clone();
				}
				outputInnerTypes[typeNo] = outputInnerType;
			}
		}

		// Do not transform 'type.binary'.

		if ( (outputPackageSpecifier == null) && (outputClassType == null) && (outputInnerTypes == null) ) {
			return null;
		} else {
			return new ClassTypeSignature(
				inputType.binary,
				( (outputPackageSpecifier == null) ? inputPackageSpecifier : outputPackageSpecifier ),
				( (outputClassType == null) ? inputClassType : outputClassType ),
				( (outputInnerTypes == null) ? inputInnerTypes : outputInnerTypes ) );
		}
	}

	protected SimpleClassTypeSignature transform(SimpleClassTypeSignature inputSignature) {
		TypeArgument[] inputArgs = inputSignature.typeArguments;
		TypeArgument[] outputArgs = null;

		for ( int argNo = 0; argNo < inputArgs.length; argNo++ ) {
			TypeArgument inputArg = inputArgs[argNo];
			TypeArgument outputArg = transform(inputArg);
			if ( outputArg != null ) {
				if ( outputArgs == null ) {
					outputArgs = inputArgs.clone();
				}
				outputArgs[argNo] = outputArg;
			}
		}

		if ( outputArgs == null ) {
			return null;
		} else {
			return new SimpleClassTypeSignature(inputSignature.identifier, outputArgs);
		}
	}

	protected TypeArgument transform(TypeArgument inputArgument) {
		ReferenceTypeSignature inputSignature = inputArgument.type;
		ReferenceTypeSignature outputSignature = transform(inputSignature);
		if ( outputSignature == null ) {
			return null;
		} else {
			return new TypeArgument(inputArgument.wildcard, outputSignature);
		}
	}

	protected JavaTypeSignature transform(JavaTypeSignature type) {
		if ( type instanceof ReferenceTypeSignature ) {
			return transform((ReferenceTypeSignature) type);
		} else {
			return null;
		}
	}

	protected ReferenceTypeSignature transform(ReferenceTypeSignature type) {
		if ( type instanceof ClassTypeSignature ) {
			return transform((ClassTypeSignature) type);

		} else if ( type instanceof ArrayTypeSignature ) {
			return transform((ArrayTypeSignature) type);

		} else {
			return null;
		}
	}

	//

	private final Set<String> unchangedDescriptors;
	private final Map<String, String> changedDescriptors;

	protected String transformDescriptor(String inputDescriptor) {
		if ( root != null ) {
			return root.transformDescriptor(inputDescriptor);
		}

		if ( unchangedDescriptors.contains(inputDescriptor) ) {
			return null;
		}

		String outputDescriptor = changedDescriptors.get(inputDescriptor);
		if ( outputDescriptor != null ) {
			return outputDescriptor;
		}

		char c = inputDescriptor.charAt(0);
		if ( c == '(' ) {
			String inputSignature = inputDescriptor.replace('$', '.');
			String outputSignature = transform(inputSignature, SignatureType.METHOD);
			if ( outputSignature != null ) {
				outputDescriptor = outputSignature.replace('.', '$');
			} else {
				// leave outputDescriptor null
			}

		} else  if ( (c == '[') || ((c == 'L') && (inputDescriptor.charAt(inputDescriptor.length() - 1) == ';')) ) {
			String inputSignature = inputDescriptor.replace('$', '.');
			String outputSignature = transform(inputSignature, SignatureType.FIELD);
			if ( outputSignature != null ) {
				outputDescriptor = outputSignature.replace('.', '$');
			} else {
				// leave outputDescriptor null
			}

		} else {
			// leave outputDescriptor null
		}

		if ( outputDescriptor == null ) {
			unchangedDescriptors.add(inputDescriptor);
		} else {
			changedDescriptors.put(inputDescriptor, outputDescriptor);
		}
		return outputDescriptor;
	}

	//
	
	private final Map<String, BundleData> bundleUpdates;

	public BundleData getBundleUpdate(String symbolicName) {
		if ( root != null ) {
			return root.getBundleUpdate(symbolicName);
		} else {
			return bundleUpdates.get(symbolicName);
		}
	}

	//

	private byte[] inputBuffer;

	protected byte[] getInputBuffer() {
		if ( root != null ) {
			return root.getInputBuffer();
		} else {
			return inputBuffer;
		}
	}

	protected void setInputBuffer(byte[] inputBuffer) {
		if ( root != null ) {
			root.setInputBuffer(inputBuffer);
		} else {
			this.inputBuffer = inputBuffer;
		}
	}

	//

	/**
	 * Read bytes from an input stream.  Answer byte data and
	 * a count of bytes read.
	 *
	 * @param inputName The name of the input stream.
	 * @param inputStream A stream to be read.
	 * @param inputCount The count of bytes to read from the stream.
	 *     {@link Action#UNKNOWN_LENGTH} if the count of
	 *     input bytes is not known.
	 *
	 * @return Byte data from the read.
	 * 
	 * @throws JakartaTransformException Indicates a read failure.
	 */
	protected ByteData read(String inputName, InputStream inputStream, int inputCount) throws JakartaTransformException {
		if ( parent != null ) {
			return parent.read(inputName, inputStream, inputCount); // throws JakartaTransformException

		} else {
			byte[] readBytes = getInputBuffer();
			ByteData readData;
			try {
				readData = FileUtils.read(inputName, inputStream, readBytes, inputCount); // throws IOException
			} catch ( IOException e ) {
				throw new JakartaTransformException("Failed to read raw bytes [ " + inputName + " ] count [ " + inputCount + " ]", e);
			}
			setInputBuffer(readData.data);
			return readData;
		}
	}

	/**
	 * Write data to an output stream.
	 * 
	 * Convert any exception thrown when attempting the write into a {@link JakartaTransformException}.
	 * 
	 * @param outputData Data to be written.
	 * @param outputStream Stream to which to write the data.
	 * 
	 * @throws JakartaTransformException Thrown in case of a write failure.
	 */
	protected void write(ByteData outputData, OutputStream outputStream) throws JakartaTransformException {
		try {
			outputStream.write(outputData.data, outputData.offset, outputData.length); // throws IOException

		} catch ( IOException e ) {
			throw new JakartaTransformException(
				"Failed to write [ " + outputData.name + " ]" +
				" at [ " + outputData.offset + " ]" +
				" count [ " + outputData.length + " ]",
				e);
		}
	}

	//

	@Override
	public InputStreamData apply(String inputName, InputStream inputStream)
		throws JakartaTransformException {

		return apply(inputName, inputStream, InputStreamData.UNKNOWN_LENGTH); // throws JakartaTransformException
	}

	@Override
	public InputStreamData apply(String inputName, InputStream inputStream, int inputCount)
		throws JakartaTransformException {

		String className = getClass().getSimpleName();
		String methodName = "apply";

		verbose("[ %s.%s ]: Requested [ %s ] [ %s ]\n", className, methodName, inputName, inputCount);
		ByteData inputData = read(inputName, inputStream, inputCount); // throws JakartaTransformException
		verbose("[ %s.%s ]: Obtained [ %s ] [ %s ] [ %s ]\n", className, methodName, inputName, inputData.length, inputData.data);

		ByteData outputData;
		try {
			outputData = apply(inputName, inputData.data, inputData.length);
			// throws JakartaTransformException
		} catch ( Throwable th ) {
			error("Transform failure [ %s ]\n", th, inputName);
			outputData = null;			
		}

		if ( outputData == null ) {
			verbose("[ %s.%s ]: Null transform\n", className, methodName);
			outputData = inputData;
		} else {
			verbose(
				"[ %s.%s ]: Active transform [ %s ] [ %s ] [ %s ]\n",
				className, methodName,
				outputData.name, outputData.length, outputData.data);
		}

		return new InputStreamData(outputData);
	}

	@Override
	public boolean apply(
		String inputName, InputStream inputStream, int inputCount,
		OutputStream outputStream) throws JakartaTransformException {

		String className = getClass().getSimpleName();
		String methodName = "apply";

		verbose("[ %s.%s ]: Requested [ %s ] [ %s ]\n", className, methodName, inputName, inputCount);
		ByteData inputData = read(inputName, inputStream, inputCount); // throws JakartaTransformException
		verbose("[ %s.%s ]: Obtained [ %s ] [ %s ]\n", className, methodName, inputName, inputData.length);

		ByteData outputData;
		try {
			outputData = apply(inputName, inputData.data, inputData.length);
			// throws JakartaTransformException
		} catch ( Throwable th ) {
			error("Transform failure [ %s ]\n", th, inputName);
			outputData = null;
		}

		boolean hasChanges;
		if ( outputData == null ) {
			verbose("[ %s.%s ]: Null transform\n", className, methodName);
			hasChanges = false;
			outputData = inputData;
		} else {
			verbose("[ %s.%s ]: Active transform [ %s ] [ %s ]\n", className, methodName, outputData.name, outputData.length);
			hasChanges = true;
		}

		write(outputData, outputStream); // throws JakartaTransformException		

		return hasChanges;
	}

	@Override
	public abstract ByteData apply(String inputName, byte[] inputBytes, int inputLength) 
		throws JakartaTransformException;

}

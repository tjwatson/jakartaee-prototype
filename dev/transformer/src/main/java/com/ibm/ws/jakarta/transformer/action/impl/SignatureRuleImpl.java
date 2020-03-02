package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.SignatureRule;

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

public class SignatureRuleImpl implements SignatureRule {
	public SignatureRuleImpl(
		LoggerImpl logger,

		Map<String, String> renames,
		Map<String, String> versions,
		Map<String, BundleData> bundleUpdates,
		Map<String, String> directStrings) {

		this.logger = logger;

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

		this.dottedPackageRenames = useRenames;
		this.slashedPackageRenames = useBinaryRenames;

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

		Map<String, String> useDirectStrings;
		if ( directStrings == null ) {
			useDirectStrings = Collections.emptyMap();
		} else {
			useDirectStrings = new HashMap<String, String>( directStrings );
		}
		this.directStrings = useDirectStrings;

		this.unchangedBinaryTypes = new HashSet<>();
		this.changedBinaryTypes = new HashMap<>();

		this.unchangedSignatures = new HashSet<>();
		this.changedSignatures = new HashMap<>();

		this.unchangedDescriptors = new HashSet<>();
		this.changedDescriptors = new HashMap<>();
	}

	//
	
	private final LoggerImpl logger;

	public LoggerImpl getLogger() {
		return logger;
	}

	public PrintStream getLogStream() {
		return getLogger().getLogStream();
	}

	public boolean getIsTerse() {
		return getLogger().getIsTerse();
	}

	public boolean getIsVerbose() {
		return getLogger().getIsVerbose();
	}

	public void log(String text, Object... parms) {
		getLogger().log(text, parms);
	}

	public void verbose(String text, Object... parms) {
		getLogger().verbose(text, parms);
	}

    public void error(String text, Object... parms) {
		getLogger().error(text, parms);
    }

    public void error(String text, Throwable th, Object... parms) {
		getLogger().error(text, th, parms);
    }

	//

	private final Map<String, BundleData> bundleUpdates;

	@Override
	public BundleData getBundleUpdate(String symbolicName) {
		return bundleUpdates.get(symbolicName);
	}

	//

	private final Map<String, String> directStrings;

	@Override
	public String getDirectString(String initialValue) {
		return directStrings.get(initialValue);
	}

	//

	// Package rename: "javax.servlet.Servlet"
	// Direct form  :  "javax.servlet"
	// Binary form:    "javax/servlet"

	protected final Map<String, String> dottedPackageRenames;
	protected final Map<String, String> slashedPackageRenames;

	@Override
	public Map<String, String> getPackageRenames() {
		return dottedPackageRenames;
	}

	//

	protected final Map<String, String> packageVersions;

	@Override
	public Map<String, String> getPackageVersions() {
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
	@Override
	public String replacePackage(String initialName) {
		return dottedPackageRenames.getOrDefault(initialName, null);
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
	@Override
	public String replaceBinaryPackage(String initialName) {
		String finalName = slashedPackageRenames.getOrDefault(initialName, null);
		// System.out.println("Initial binary [ " + initialName + " ] Final [ " + finalName + " ]");
		return finalName;
	}
	
	@Override
	public String replacePackages(String text) {
	    return replacePackages(text, this.dottedPackageRenames);
	}
	   
	/**
	 * Replace all embedded packages of specified text with replacement
	 * packages.
	 *
	 * @param text String embedding zero, one, or more package names.
	 * @param packageRenames map of names and replacement values
	 * @return The text with all embedded package names replaced.  Null if no
	 *     replacements were performed.
	 */
	@Override
	public String replacePackages(String text, Map<String, String> packageRenames ) {
		// System.out.println("Initial text [ " + text + " ]");

		String initialText = text;

		for ( Map.Entry<String, String> renameEntry : packageRenames.entrySet() ) {
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
				
                if ( !ActionImpl.isTruePackageMatch(text, matchStart, keyLen) ) {
                    lastMatchEnd = matchStart + keyLen;
                    continue;
                }

				String value = renameEntry.getValue();
				int valueLen = value.length();

				String head = text.substring(0, matchStart);
				String tail = text.substring(matchStart + keyLen);
				text = head + value + tail;

				lastMatchEnd = matchStart + valueLen;
				textLimit += (valueLen - keyLen);

				// System.out.println("Next text [ " + text + " ]");
			}
		}

		if ( initialText == text) {
		    //System.out.println("RETURN Final text is unchanged");
			return null;
		} else {
			//System.out.println("RETURN Final text [ " + text + " ]");
			return text;
		}
	}

    //

	private final Map<String, String> changedBinaryTypes;
	private final Set<String> unchangedBinaryTypes;

	@Override
	public String transformConstantAsBinaryType(String inputConstant) {
	    return transformConstantAsBinaryType(inputConstant, NO_SIMPLE_SUBSTITUTION);
	}

	@Override
	public String transformConstantAsBinaryType(String inputConstant, boolean allowSimpleSubstitution) {
	    try {
	        return transformBinaryType(inputConstant, allowSimpleSubstitution);
	    } catch ( Throwable th ) {
	        verbose("Failed to parse constant as resource reference [ %s ]: %s", inputConstant, th.getMessage());
	        return null;
	    }
	}

	@Override
	public String transformBinaryType(String inputName) {
	    return transformBinaryType(inputName, NO_SIMPLE_SUBSTITUTION);
	}
	   
	/**
	 * Modify a fully qualified type name according to the package rename table.
	 * Answer either the transformed type name, or, if the type name was not changed,
	 * a wrapped null.
	 * 
	 * @param inputName A fully qualified type name which is to be transformed.
	 *
	 * @return The transformed type name, or a wrapped null if no changed was made.
	 */
	protected String transformBinaryType(String inputName, boolean allowSimpleSubstitution) {
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
		
		if ( (outputName == null) && allowSimpleSubstitution ) {
		    outputName = replacePackages(inputName, slashedPackageRenames);
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

	//

	@Override
	public String transformConstantAsDescriptor(String inputConstant) {
	    return transformConstantAsDescriptor(inputConstant, NO_SIMPLE_SUBSTITUTION);
	}
	
	@Override
	public String transformConstantAsDescriptor(String inputConstant, boolean allowSimpleSubstitution) {
		try {
			return transformDescriptor(inputConstant, allowSimpleSubstitution);
		} catch ( Throwable th ) {
			verbose("Failed to parse constant as descriptor [ %s ]: %s", inputConstant, th.getMessage());
			return null;
		}
	}

	private final Set<String> unchangedDescriptors;
	private final Map<String, String> changedDescriptors;

	@Override
	public String transformDescriptor(String inputDescriptor) {
	    return transformDescriptor(inputDescriptor, NO_SIMPLE_SUBSTITUTION);
	}

	@Override
	public String transformDescriptor(String inputDescriptor, boolean allowSimpleSubstitution) {
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
		
		if ( (outputDescriptor == null) && allowSimpleSubstitution ) {
		    outputDescriptor = replacePackages(inputDescriptor, dottedPackageRenames);
		}

		if ( outputDescriptor == null ) {
			unchangedDescriptors.add(inputDescriptor);
		} else {
			changedDescriptors.put(inputDescriptor, outputDescriptor);
		}
		return outputDescriptor;
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
	@Override
	public String transform(String input, SignatureType signatureType) {
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

	@Override
	public ClassSignature transform(ClassSignature classSignature) {
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

	@Override
	public FieldSignature transform(FieldSignature fieldSignature) {
		ReferenceTypeSignature inputType = fieldSignature.type;
		ReferenceTypeSignature outputType = transform(inputType);

		if ( outputType == null ) {
			return null;
		} else {
			return new FieldSignature(outputType);
		}
	}

	@Override
	public MethodSignature transform(MethodSignature methodSignature) {
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

	@Override
	public Result transform(Result type) {
		if ( type instanceof JavaTypeSignature ) {
			return transform((JavaTypeSignature) type);
		} else {
			return null;
		}
	}

	@Override
	public ThrowsSignature transform(ThrowsSignature type) {
		if ( type instanceof ClassTypeSignature ) {
			return transform((ClassTypeSignature) type);
		} else {
			return null;
		}
	}
	
	@Override
	public ArrayTypeSignature transform(ArrayTypeSignature inputType) {
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

	@Override
	public TypeParameter transform(TypeParameter inputTypeParameter) {
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

	@Override
	public ClassTypeSignature transform(ClassTypeSignature inputType) {
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

	@Override
	public SimpleClassTypeSignature transform(SimpleClassTypeSignature inputSignature) {
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

	@Override
	public TypeArgument transform(TypeArgument inputArgument) {
		ReferenceTypeSignature inputSignature = inputArgument.type;
		ReferenceTypeSignature outputSignature = transform(inputSignature);
		if ( outputSignature == null ) {
			return null;
		} else {
			return new TypeArgument(inputArgument.wildcard, outputSignature);
		}
	}

	@Override
	public JavaTypeSignature transform(JavaTypeSignature type) {
		if ( type instanceof ReferenceTypeSignature ) {
			return transform((ReferenceTypeSignature) type);
		} else {
			return null;
		}
	}

	@Override
	public ReferenceTypeSignature transform(ReferenceTypeSignature type) {
		if ( type instanceof ClassTypeSignature ) {
			return transform((ClassTypeSignature) type);

		} else if ( type instanceof ArrayTypeSignature ) {
			return transform((ArrayTypeSignature) type);

		} else {
			return null;
		}
	}
}

package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.DataInput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.ClassAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;
import com.ibm.ws.jakarta.transformer.util.FileUtils;

import aQute.bnd.classfile.AnnotationDefaultAttribute;
import aQute.bnd.classfile.AnnotationInfo;
import aQute.bnd.classfile.AnnotationsAttribute;
import aQute.bnd.classfile.Attribute;
import aQute.bnd.classfile.ClassFile;
import aQute.bnd.classfile.CodeAttribute;
import aQute.bnd.classfile.CodeAttribute.ExceptionHandler;
import aQute.bnd.classfile.ConstantPool;
import aQute.bnd.classfile.ConstantPool.ClassInfo;
import aQute.bnd.classfile.ConstantPool.MethodTypeInfo;
import aQute.bnd.classfile.ConstantPool.NameAndTypeInfo;
import aQute.bnd.classfile.ConstantPool.StringInfo;
import aQute.bnd.classfile.ElementValueInfo;
import aQute.bnd.classfile.ElementValueInfo.EnumConst;
import aQute.bnd.classfile.ElementValueInfo.ResultConst;
import aQute.bnd.classfile.EnclosingMethodAttribute;
import aQute.bnd.classfile.ExceptionsAttribute;
import aQute.bnd.classfile.FieldInfo;
import aQute.bnd.classfile.InnerClassesAttribute;
import aQute.bnd.classfile.InnerClassesAttribute.InnerClass;
import aQute.bnd.classfile.LocalVariableTableAttribute;
import aQute.bnd.classfile.LocalVariableTableAttribute.LocalVariable;
import aQute.bnd.classfile.LocalVariableTypeTableAttribute;
import aQute.bnd.classfile.LocalVariableTypeTableAttribute.LocalVariableType;
import aQute.bnd.classfile.MemberInfo;
import aQute.bnd.classfile.MethodInfo;
import aQute.bnd.classfile.ModuleAttribute;
import aQute.bnd.classfile.ModulePackagesAttribute;
import aQute.bnd.classfile.NestHostAttribute;
import aQute.bnd.classfile.NestMembersAttribute;
import aQute.bnd.classfile.ParameterAnnotationInfo;
import aQute.bnd.classfile.ParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleTypeAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleTypeAnnotationsAttribute;
import aQute.bnd.classfile.SignatureAttribute;
import aQute.bnd.classfile.StackMapTableAttribute;
import aQute.bnd.classfile.StackMapTableAttribute.AppendFrame;
import aQute.bnd.classfile.StackMapTableAttribute.FullFrame;
import aQute.bnd.classfile.StackMapTableAttribute.ObjectVariableInfo;
import aQute.bnd.classfile.StackMapTableAttribute.SameLocals1StackItemFrame;
import aQute.bnd.classfile.StackMapTableAttribute.SameLocals1StackItemFrameExtended;
import aQute.bnd.classfile.StackMapTableAttribute.StackMapFrame;
import aQute.bnd.classfile.StackMapTableAttribute.VerificationTypeInfo;
import aQute.bnd.classfile.TypeAnnotationInfo;
import aQute.bnd.classfile.TypeAnnotationsAttribute;
import aQute.bnd.classfile.builder.ClassFileBuilder;
import aQute.bnd.classfile.builder.MutableConstantPool;
import aQute.lib.io.ByteBufferDataInput;
import aQute.lib.io.ByteBufferDataOutput;

/**
 * Transform class bytes.
 */
public class ClassActionImpl extends ActionImpl implements ClassAction {

	protected static final int DUMP_WIDTH = 16;

	protected void dump(byte[] bytes, int offset, int length) {
		PrintStream useOutput = getLogStream();

		while ( length > 0 ) {
			int nextWidth = ( (length > DUMP_WIDTH) ? DUMP_WIDTH : length );
			dumpLine(bytes, offset, nextWidth, useOutput);
			offset += nextWidth;
			length -= nextWidth;
		}
	}

	protected void dumpLine(byte[] bytes, int offset, int width, PrintStream useOutput) {
		for ( int byteNo = 0; byteNo < width; byteNo++ ) {
			byte nextByte = bytes[ offset + byteNo ];
			useOutput.printf("%02x", nextByte);
		}
		useOutput.println();
	}

	public ClassActionImpl(ActionImpl parentAction) {
		super(parentAction);
	}

	public ClassActionImpl(Set<String> includes, Set<String> excludes, Map<String, String> renames,
			Map<String, String> versions, Map<String, BundleData> bundleUpdates) {
		super(includes, excludes, renames, versions, bundleUpdates);
	}

	public ClassActionImpl(
		PrintStream logStream, boolean isTerse, boolean isVerbose,
		Set<String> includes, Set<String> excludes, Map<String, String> renames,
		Map<String, String> versions, Map<String, BundleData> bundleUpdates) {

		super(logStream, isTerse, isVerbose,
			  includes, excludes,
			  renames, versions, bundleUpdates);
	}

	//

	public String getName() {
		return "Class Action";
	}

	//

	@Override
	protected ClassChangesImpl newChanges() {
		return new ClassChangesImpl();
	}

	@Override
	public ClassChangesImpl getChanges() {
		return (ClassChangesImpl) super.getChanges();
	}

	protected void setClassNames(String inputClassName, String outputClassName) {
		ClassChangesImpl useChanges = getChanges();
		useChanges.setInputClassName(inputClassName);
		useChanges.setOutputClassName(outputClassName);
	}

	protected void setSuperClassNames(String inputSuperName, String outputSuperName) {
		ClassChangesImpl useChanges = getChanges();
		useChanges.setInputSuperName(inputSuperName);
		useChanges.setOutputSuperName(outputSuperName);
	}	

	protected void addModifiedInterface() {
		getChanges().addModifiedInterface();
	}

	protected void addModifiedField() {
		getChanges().addModifiedField();
	}

	protected void addModifiedMethod() {
		getChanges().addModifiedMethod();
	}

	protected void addModifiedAttribute() {
		getChanges().addModifiedAttribute();
	}

	protected void setModifiedConstants(int modifiedConstants) {
		getChanges().setModifiedConstants(modifiedConstants);
	}

	//

	@Override
	public boolean accept(String resourceName) {
		return resourceName.endsWith(".class");
	}

	//

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength)
		throws JakartaTransformException {

		clearChanges();

		if ( getIsVerbose() ) {
			verbose("Read [ %s ] Bytes [ %s ] [ %s ]\n", inputName, inputLength, inputBytes);
			// dump(inputBytes, 0, inputLength);
		}

		ClassFile inputClass;
		try {
			DataInput inputClassData = ByteBufferDataInput.wrap(inputBytes, 0, inputLength);
			inputClass = ClassFile.parseClassFile(inputClassData); // throws IOException
		} catch ( IOException e ) {
			error("Failed to parse raw class bytes [ %s ]\n", e, inputName);
			return null;
		}

		if ( getIsVerbose() ) {
			verbose("Class [ %s ] as [ %s ]\n", inputName, inputClass.this_class);
			verbose("  Super [ %s ]\n", inputClass.super_class);
			if ( inputClass.interfaces != null ) {
				verbose("  Interfaces [ %s ]\n", inputClass.interfaces.length);
				for ( String interfaceName : inputClass.interfaces ) {
					verbose("    [ %s ]\n", interfaceName);
				}
			}
			if ( inputClass.fields != null ) {
				verbose("  Fields [ %s ]\n", inputClass.fields.length);
				for ( FieldInfo field : inputClass.fields ) {
					verbose("    [ %s ] [ %s ]\n", field.name, field.descriptor);
				}
			}
			if ( inputClass.methods != null ) {
				verbose("  Methods [ %s ]\n", inputClass.methods.length);
				for ( MethodInfo method : inputClass.methods) {
					verbose("    [ %s ] [ %s ]\n", method.name, method.descriptor);
				}
			}
		}

		ClassFileBuilder classBuilder = new ClassFileBuilder(inputClass);

		// Transform the class declaration ...

		String outputName;

		String inputClassName = classBuilder.this_class();
		String outputClassName = transformBinaryType(inputClassName);

		if ( outputClassName != null ) {
			classBuilder.this_class(outputClassName);
			outputName = asResourceName(outputClassName);
		} else {
			outputClassName = inputClassName;
			outputName = inputName;
		}

		setClassNames(inputClassName, outputClassName);
		setResourceNames(inputName, outputName);

		verbose("%s\n", classBuilder);

		String inputSuperName = classBuilder.super_class(); 
		if ( inputSuperName != null ) {
			String outputSuperName = transformBinaryType(inputSuperName);
			if ( outputSuperName != null ) {
				classBuilder.super_class(outputSuperName);
			} else {
				outputSuperName = inputSuperName;
			}

			setSuperClassNames(inputSuperName, outputSuperName);

			if ( !outputSuperName.equals("java/lang/Object") ) {
				verbose(" extends %s\n", outputSuperName);
			}
		}

		List<String> interfaces = classBuilder.interfaces();
		if ( !interfaces.isEmpty() ) {
			ListIterator<String> interfaceNames = interfaces.listIterator();
			while ( interfaceNames.hasNext() ) {
				String interfaceName = transformBinaryType( interfaceNames.next() );
				if ( interfaceName != null ) {
					interfaceNames.set(interfaceName);
					addModifiedInterface();
				}
			}

			verbose(" implements %s\n", interfaces);
		}

		verbose("\n");

		// Transform members ...

		ListIterator<FieldInfo> fields = classBuilder.fields().listIterator();
		while ( fields.hasNext() ) {
			FieldInfo inputField = fields.next();
			FieldInfo outputField = transform( inputField, FieldInfo::new, SignatureType.FIELD );
			if ( outputField != null ) {
				fields.set(outputField);
				addModifiedField();
				verbose( "    %s -> %s\n", inputField, outputField);

			}
		}

		ListIterator<MethodInfo> methods = classBuilder.methods().listIterator();
		while ( methods.hasNext() ) {
			MethodInfo inputMethod = methods.next();
			MethodInfo outputMethod = transform( inputMethod, MethodInfo::new, SignatureType.METHOD );
			if ( outputMethod != null ) {
				methods.set(outputMethod);
				addModifiedMethod();
				verbose( "    %s -> %s\n", inputMethod, outputMethod);
			}
		}

		// verbose("  <<class>>\n");

		// Transform attributes ...

		ListIterator<Attribute> attributes = classBuilder.attributes().listIterator();
		while ( attributes.hasNext() ) {
			Attribute inputAttribute = attributes.next();
			Attribute outputAttribute = transform(inputAttribute, SignatureType.CLASS);
			if ( outputAttribute != null ) {
				attributes.set(outputAttribute);
				addModifiedAttribute();
				verbose( "    %s -> %s\n", inputAttribute, outputAttribute);
			}
		}

		MutableConstantPool constants = classBuilder.constant_pool();
		verbose("    Constant Pool size: %s\n", constants.size()); 

		int modifiedConstants = transform(constants);
		if ( modifiedConstants > 0 ) {
			setModifiedConstants(modifiedConstants);
		}

		if ( !hasChanges() ) {
			log("    Class size: %s %s\n", inputName, inputLength);
			return null;
		}

		ClassFile outputClass = classBuilder.build();

		ByteBufferDataOutput outputClassData = new ByteBufferDataOutput( inputBytes.length + FileUtils.PAGE_SIZE );
		try {
			outputClass.write(outputClassData); // throws IOException
		} catch ( IOException e ) {
			throw new JakartaTransformException("Failed to write transformed class bytes", e);
		}

		byte[] outputBytes = outputClassData.toByteArray();
		log("    Class size: %s: %s -> %s\n", inputName, inputBytes.length, outputBytes.length);
		return new ByteData(outputName, outputBytes, 0, outputBytes.length);
	}

	//

	private <M extends MemberInfo> M transform(
		M member,
		MemberInfo.Constructor<M> constructor,
		SignatureType signatureType) {

		String inputDescriptor = member.descriptor;
		String outputDescriptor = transformDescriptor(inputDescriptor);
		if ( outputDescriptor != null ) {
			verbose("  %s %s -> %s\n", member.name, member.descriptor, outputDescriptor);
		}

		Attribute[] inputAttributes = member.attributes;
		Attribute[] outputAttributes = transform(inputAttributes, signatureType);

		if ( (outputDescriptor == null) && (outputAttributes == null) ) {
			return null;
		}

		return constructor.init(
			member.access, member.name,
			((outputDescriptor == null) ? inputDescriptor : outputDescriptor),
			((outputAttributes == null) ? inputAttributes : outputAttributes) );
	}

	private Attribute[] transform(Attribute[] inputAttributes, SignatureType signatureType) {
		Attribute[] outputAttributes = null;

		for ( int attributeNo = 0; attributeNo < inputAttributes.length; attributeNo++ ) {
			Attribute inputAttribute = inputAttributes[attributeNo];
			Attribute outputAttribute = transform(inputAttribute, signatureType);
			if ( outputAttribute != null ) {
				if ( outputAttributes == null ) {
					outputAttributes = inputAttributes.clone();
				}
				outputAttributes[attributeNo] = outputAttribute;

				verbose("    %s -> %s\n", inputAttribute, outputAttribute);
			}
		}

		return outputAttributes;
	}

	private Attribute transform(Attribute attr, SignatureType signatureType) {
		switch ( attr.name() ) {
			case SignatureAttribute.NAME: {
				SignatureAttribute inputAttribute = (SignatureAttribute) attr;
				String outputSignature = transform(inputAttribute.signature, signatureType);
				return ( (outputSignature == null) ? null : new SignatureAttribute(outputSignature) );
			}

			case ExceptionsAttribute.NAME: {
				ExceptionsAttribute inputAttribute = (ExceptionsAttribute) attr;
				String[] inputExceptions = inputAttribute.exceptions;
				String[] outputExceptions = null;
				for ( int exNo = 0; exNo < inputExceptions.length; exNo++ ) {
					String exception = transformBinaryType( inputExceptions[exNo] );
					if ( exception != null) {
						if ( outputExceptions == null ) {
							outputExceptions = inputExceptions.clone();
						}
						outputExceptions[exNo] = exception;
					}
				}

				return ( (outputExceptions == null) ? null : new ExceptionsAttribute(inputExceptions) );
			}

			case CodeAttribute.NAME: {
				CodeAttribute attribute = (CodeAttribute) attr;

				ExceptionHandler[] inputHandlers = attribute.exception_table;
				ExceptionHandler[] outputHandlers = null;

				for ( int handlerNo = 0; handlerNo < inputHandlers.length; handlerNo++ ) {
					ExceptionHandler inputHandler = inputHandlers[handlerNo];
					String inputCatchType = inputHandler.catch_type;
					if ( inputCatchType != null ) {
						String outputCatchType = transformBinaryType(inputCatchType);
						if ( outputCatchType != null ) {
							if ( outputHandlers == null ) {
								outputHandlers = inputHandlers.clone();
							}
							outputHandlers[handlerNo] = new ExceptionHandler(
								inputHandler.start_pc, inputHandler.end_pc, inputHandler.handler_pc,
								outputCatchType);
						}
					}
				}

				// TODO Maybe intercept Class.forName/etc calls at
				// runtime to rename types

				Attribute[] inputAttributes = attribute.attributes;
				Attribute[] outputAttributes = transform(inputAttributes, SignatureType.METHOD);

				if ( (outputHandlers == null) && (outputAttributes == null) ) {
					return null;
				} else {
					return new CodeAttribute(
						attribute.max_stack, attribute.max_locals, attribute.code,
						( (outputHandlers == null) ? inputHandlers : outputHandlers ),
						( (outputAttributes == null) ? inputAttributes : outputAttributes) );
				}
			}

			case EnclosingMethodAttribute.NAME: {
				EnclosingMethodAttribute attribute = (EnclosingMethodAttribute) attr;
				String inputDescriptor = attribute.method_descriptor;
				if ( inputDescriptor == null ) {
					return null;
				}
				String outputDescriptor = transformDescriptor(inputDescriptor);
				if ( outputDescriptor == null ) {
					return null;
				} else {
					return new EnclosingMethodAttribute(
						attribute.class_name, attribute.method_name, outputDescriptor);
				}
			}

			case StackMapTableAttribute.NAME: {
				StackMapTableAttribute inputAttribute = (StackMapTableAttribute) attr;

				StackMapFrame[] inputFrames = inputAttribute.entries;
				StackMapFrame[] outputFrames = null;

				for ( int frameNo = 0; frameNo < inputFrames.length; frameNo++ ) {
					StackMapFrame inputFrame = inputFrames[frameNo];
					switch (inputFrame.type()) {
						case StackMapFrame.SAME_LOCALS_1_STACK_ITEM: {
							SameLocals1StackItemFrame frame = (SameLocals1StackItemFrame) inputFrame;
							VerificationTypeInfo stack = transform(frame.stack);
							if (stack != null) {
								if ( outputFrames == null ) {
									outputFrames = inputFrames.clone();
								}
								outputFrames[frameNo] = new SameLocals1StackItemFrame(frame.tag, stack);
							}
							break;
						}
						case StackMapFrame.SAME_LOCALS_1_STACK_ITEM_EXTENDED: {
							SameLocals1StackItemFrameExtended frame = (SameLocals1StackItemFrameExtended) inputFrame;
							VerificationTypeInfo stack = transform(frame.stack);
							if (stack != null) {
								if ( outputFrames == null ) {
									outputFrames = inputFrames.clone();
								}
								outputFrames[frameNo] = new SameLocals1StackItemFrameExtended(frame.tag, frame.delta, stack);
							}
							break;
						}
						case StackMapFrame.APPEND: {
							AppendFrame frame = (AppendFrame) inputFrame;
							VerificationTypeInfo[] locals = transform(frame.locals);
							if (locals != null) {
								if ( outputFrames == null ) {
									outputFrames = inputFrames.clone();
								}
								outputFrames[frameNo] = new AppendFrame(frame.tag, frame.delta, locals);
							}
							break;
						}
						case StackMapFrame.FULL_FRAME: {
							FullFrame frame = (FullFrame) inputFrame;
							VerificationTypeInfo[] locals = transform(frame.locals);
							VerificationTypeInfo[] stack = transform(frame.stack);
							if ((locals != null) || (stack != null)) {
								if ( outputFrames == null ) {
									outputFrames = inputFrames.clone();
								}
								outputFrames[frameNo] = new FullFrame(
									frame.tag, frame.delta,
									( (locals == null) ? frame.locals : locals ),
									( (stack == null) ? frame.stack : stack ) );
							}
							break;
						}
						default:
							break;
					}
				}
				if ( outputFrames == null ) {
					return null;
				} else {
					return new StackMapTableAttribute(outputFrames);
				}
			}

			case InnerClassesAttribute.NAME: {
				InnerClassesAttribute inputAttribute = (InnerClassesAttribute) attr;

				InnerClass[] inputClasses = inputAttribute.classes;
				InnerClass[] outputClasses = null;

				for ( int classNo = 0; classNo < inputClasses.length; classNo++ ) {
					InnerClass inputClass = inputClasses[classNo];

					String inputInnerClass = inputClass.inner_class;
					String outputInnerClass =
						( (inputInnerClass == null) ? null : transformBinaryType(inputInnerClass) );

					String inputOuterClass = inputClass.outer_class;
					String outputOuterClass =
						( (inputOuterClass == null) ? null : transformBinaryType(inputOuterClass) );

					if ( (outputInnerClass != null) || (outputOuterClass != null) ) {
						if ( outputClasses == null ) {
							outputClasses = inputClasses.clone();
						}
						outputClasses[classNo] = new InnerClass(
							( (outputInnerClass == null) ? inputInnerClass : outputInnerClass ),
							( (outputOuterClass == null) ? inputOuterClass : outputOuterClass ),
							inputClass.inner_name, inputClass.inner_access);
					}
				}

				if ( outputClasses == null ) {
					return null;
				} else {
					return new InnerClassesAttribute(outputClasses);
				}
			}

			case LocalVariableTableAttribute.NAME: {
				LocalVariableTableAttribute inputAttribute = (LocalVariableTableAttribute) attr;

				LocalVariable[] inputVariables = inputAttribute.local_variable_table;
				LocalVariable[] outputVariables = null;

				for ( int varNo = 0; varNo < inputVariables.length; varNo++ ) {
					LocalVariable inputVariable = inputVariables[varNo];
					String outputDescriptor = transformDescriptor(inputVariable.descriptor);
					if ( outputDescriptor != null ) {
						if ( outputVariables == null ) {
							outputVariables = inputVariables.clone();
						}
						outputVariables[varNo] = new LocalVariable(
							inputVariable.start_pc, inputVariable.length,
							inputVariable.name, outputDescriptor,
							inputVariable.index);
					}
				}

				if ( outputVariables == null ) {
					return null;
				} else {
					return new LocalVariableTableAttribute(outputVariables);
				}
			}

			case LocalVariableTypeTableAttribute.NAME: {
				LocalVariableTypeTableAttribute inputAttribute =
					(LocalVariableTypeTableAttribute) attr;

				LocalVariableType[] inputTypes = inputAttribute.local_variable_type_table;
				LocalVariableType[] outputTypes = null;

				for ( int varNo = 0; varNo < inputTypes.length; varNo++ ) {
					LocalVariableType inputType = inputTypes[varNo];
					String outputSignature = transform(inputType.signature, SignatureType.FIELD);

					if ( outputSignature != null ) {
						if ( outputTypes == null ) {
							outputTypes = inputTypes.clone();
						}
						outputTypes[varNo] = new LocalVariableType(
							inputType.start_pc, inputType.length,
							inputType.name, outputSignature,
							inputType.index);
					}
				}

				if ( outputTypes == null ) {
					return null;
				} else {
					return new LocalVariableTypeTableAttribute(outputTypes);
				}
			}

			case RuntimeVisibleAnnotationsAttribute.NAME: {
				RuntimeVisibleAnnotationsAttribute inputAttribute =
					(RuntimeVisibleAnnotationsAttribute) attr;
				RuntimeVisibleAnnotationsAttribute outputAttribute =
					transform(inputAttribute, RuntimeVisibleAnnotationsAttribute::new);
				return outputAttribute;
			}

			case RuntimeInvisibleAnnotationsAttribute.NAME: {
				RuntimeInvisibleAnnotationsAttribute inputAttribute =
					(RuntimeInvisibleAnnotationsAttribute) attr;
				RuntimeInvisibleAnnotationsAttribute outputAttribute =
					transform(inputAttribute, RuntimeInvisibleAnnotationsAttribute::new);
				return outputAttribute;
			}

			case RuntimeVisibleParameterAnnotationsAttribute.NAME: {
				RuntimeVisibleParameterAnnotationsAttribute inputAttribute =
					(RuntimeVisibleParameterAnnotationsAttribute) attr;
				RuntimeVisibleParameterAnnotationsAttribute outputAttribute =
					transform( inputAttribute, RuntimeVisibleParameterAnnotationsAttribute::new );
				return outputAttribute;
			}

			case RuntimeInvisibleParameterAnnotationsAttribute.NAME: {
				RuntimeInvisibleParameterAnnotationsAttribute inputAttribute =
					(RuntimeInvisibleParameterAnnotationsAttribute) attr;
				RuntimeInvisibleParameterAnnotationsAttribute outputAttribute =
					transform( inputAttribute, RuntimeInvisibleParameterAnnotationsAttribute::new);
				return outputAttribute;
			}

			case RuntimeVisibleTypeAnnotationsAttribute.NAME: {
				RuntimeVisibleTypeAnnotationsAttribute inputAttribute =
					(RuntimeVisibleTypeAnnotationsAttribute) attr;
				RuntimeVisibleTypeAnnotationsAttribute outputAttribute =
					transform( inputAttribute, RuntimeVisibleTypeAnnotationsAttribute::new );
				return outputAttribute;
			}

			case RuntimeInvisibleTypeAnnotationsAttribute.NAME: {
				RuntimeInvisibleTypeAnnotationsAttribute inputAttribute =
					(RuntimeInvisibleTypeAnnotationsAttribute) attr;
				RuntimeInvisibleTypeAnnotationsAttribute outputAttribute =
					transform(inputAttribute, RuntimeInvisibleTypeAnnotationsAttribute::new);
				return outputAttribute;
			}

			case AnnotationDefaultAttribute.NAME: {
				AnnotationDefaultAttribute inputAttribute =
					(AnnotationDefaultAttribute) attr;
				Object outputValue = transformElementValue(inputAttribute.value);
				return ( (outputValue == null) ? null : new AnnotationDefaultAttribute(outputValue) );
			}

			case ModuleAttribute.NAME:
			case ModulePackagesAttribute.NAME:
				// TODO Handle module metadata in case some
				// used by some Java EE 8/Jakarta EE 8 artifacts.
				break;

			case NestHostAttribute.NAME:
			case NestMembersAttribute.NAME: {
				// TODO These Java SE 9+ attributes should not be used
				// by Java EE 8/Jakarta EE 8 artifacts, so
				// we ignore them for now.
				break;
			}

			default:
				break;
		}

		return null;
	}

	private <A extends AnnotationsAttribute> A transform(
		A inputAttribute,
		AnnotationsAttribute.Constructor<A> constructor) {

		AnnotationInfo[] outputAnnotations = transform(inputAttribute.annotations);

		return ( (outputAnnotations == null) ? null : constructor.init(outputAnnotations) );
	}

	private AnnotationInfo[] transform(AnnotationInfo[] inputAnnotations) {
		AnnotationInfo[] outputAnnotations = null;

		for ( int annoNo = 0; annoNo < inputAnnotations.length; annoNo++ ) {
			AnnotationInfo inputAnnotation = inputAnnotations[annoNo];
			AnnotationInfo outputAnnotation = transform(inputAnnotation, AnnotationInfo::new );
			if ( outputAnnotation != null ) {
				if ( outputAnnotations == null ) {
					outputAnnotations = inputAnnotations.clone();
				}
				outputAnnotations[annoNo] = outputAnnotation;
			}
		}
		
		return outputAnnotations;
	}

	private <A extends ParameterAnnotationsAttribute> A transform(
		A attribute,
		ParameterAnnotationsAttribute.Constructor<A> constructor) {

		ParameterAnnotationInfo[] outputParmAnnotations =
			transform(attribute.parameter_annotations);

		if ( outputParmAnnotations == null ) {
			return null;
		} else {
			return constructor.init(outputParmAnnotations);
		}
	}

	private ParameterAnnotationInfo[] transform(ParameterAnnotationInfo[] inputParmAnnotations) {
		ParameterAnnotationInfo[] outputParmAnnotations = null;

		for ( int parmNo = 0; parmNo < inputParmAnnotations.length; parmNo++ ) {
			ParameterAnnotationInfo inputParmAnnotation = inputParmAnnotations[parmNo];
			AnnotationInfo[] outputAnnotations = transform(inputParmAnnotation.annotations);
			if ( outputAnnotations != null ) {
				if ( outputParmAnnotations == null ) {
					outputParmAnnotations = inputParmAnnotations.clone();
				}
				outputParmAnnotations[parmNo] = new ParameterAnnotationInfo(
					inputParmAnnotation.parameter, outputAnnotations);
			}
		}

		return outputParmAnnotations;
	}

	private <A extends TypeAnnotationsAttribute> A transform(
		A inputAttribute,
		TypeAnnotationsAttribute.Constructor<A> constructor) {

		TypeAnnotationInfo[] outputAnnotations = transform(inputAttribute.type_annotations);

		if ( outputAnnotations == null ) {
			return null;
		} else {
			return constructor.init(outputAnnotations);
		}
	}

	private TypeAnnotationInfo[] transform(TypeAnnotationInfo[] inputAnnotations) {
		TypeAnnotationInfo[] outputAnnotations = null;

		for ( int annoNo = 0; annoNo < inputAnnotations.length; annoNo++ ) {
			TypeAnnotationInfo inputAnnotation = inputAnnotations[annoNo];
			TypeAnnotationInfo outputAnnotation = transform(
				inputAnnotation,
				(type, values) -> new TypeAnnotationInfo(
					inputAnnotation.target_type, inputAnnotation.target_info,
					inputAnnotation.target_index, inputAnnotation.type_path,
					type, values) );

			if ( outputAnnotation != null ) {
				if ( outputAnnotations == null ) {
					outputAnnotations = inputAnnotations.clone();
				}
				outputAnnotations[annoNo] = outputAnnotation;
			}
		}

		return outputAnnotations;
	}

	private <A extends AnnotationInfo> A transform(
		A inputAnnotation,
		AnnotationInfo.Constructor<A> constructor) {

		String inputType = inputAnnotation.type;
		String outputType = transformDescriptor(inputType);

		ElementValueInfo[] inputValues = inputAnnotation.values;
		ElementValueInfo[] outputValues = transform(inputValues);

		if ( (outputType == null) && (outputValues == null) ) {
			return null;
		} else {
			return constructor.init(
				( (outputType == null) ? inputType : outputType ),
				( (outputValues == null) ? inputValues : outputValues ) );
		}
	}

	private ElementValueInfo[] transform(ElementValueInfo[] inputElementValues) {
		ElementValueInfo[] outputElementValues = null;

		for ( int valueNo = 0; valueNo < inputElementValues.length; valueNo++ ) {
			ElementValueInfo inputElementValue = inputElementValues[valueNo];
			Object outputValue = transformElementValue(inputElementValue.value);

			if ( outputValue != null ) {
				if ( outputElementValues == null ) {
					outputElementValues = inputElementValues.clone();
				}
				outputElementValues[valueNo] =
					new ElementValueInfo(inputElementValue.name, outputValue);
			}
		}

		return outputElementValues;
	}

	private Object transformElementValue(Object inputValue) {
		if ( inputValue instanceof EnumConst ) {
			EnumConst enumValue = (EnumConst) inputValue;
			String inputType= enumValue.type;
			String outputType = transformDescriptor(inputType);
			if ( outputType == null ) {
				return null;
			} else {
				return new EnumConst(outputType, enumValue.name);
			}

		} else if ( inputValue instanceof ResultConst ) {
			ResultConst resultValue = (ResultConst) inputValue;
			String inputDescriptor = resultValue.descriptor;
			String outputDescriptor = transformDescriptor(inputDescriptor);
			if ( outputDescriptor == null ) {
				return null;
			} else {
				return new ResultConst(outputDescriptor);
			}

		} else if ( inputValue instanceof AnnotationInfo ) {
			AnnotationInfo annotationValue = (AnnotationInfo) inputValue;
			return transform(annotationValue, AnnotationInfo::new);

		} else if (inputValue instanceof Object[]) {
			Object[] inputElementValues = ((Object[]) inputValue);
			Object[] outputElementValues = null;

			for ( int valueNo = 0; valueNo < inputElementValues.length; valueNo++ ) {
				Object outputElementValue = transformElementValue(inputElementValues[valueNo]);
				if ( outputElementValue != null ) {
					if ( outputElementValues == null ) {
						outputElementValues = inputElementValues.clone();
					}
					outputElementValues[valueNo] = outputElementValue;
				}
			}

			return outputElementValues;

		} else {
			return null;
		}
	}

	//
	
	private VerificationTypeInfo[] transform(VerificationTypeInfo[] inputVtis) {
		VerificationTypeInfo[] outputVtis = null;

		for ( int vtiNo = 0; vtiNo < inputVtis.length; vtiNo++ ) {
			VerificationTypeInfo inputVti =  inputVtis[vtiNo];
			VerificationTypeInfo outputVti = transform(inputVti);
			if ( outputVti != null ) {
				if ( outputVtis == null ) {
					outputVtis = inputVtis.clone();
				}
				outputVtis[vtiNo] = outputVti;
			}
		}

		return outputVtis;
	}

	private VerificationTypeInfo transform(VerificationTypeInfo vti) {
		if ( !(vti instanceof ObjectVariableInfo) ) {
			return null;
		}
		ObjectVariableInfo inputOvi = (ObjectVariableInfo) vti;

		String inputType = inputOvi.type;
		if ( inputType == null ) {
			return null;
		}

		String outputType = transformBinaryType(inputType);
		if ( outputType == null ) {
			return null;
		} else {
			return new ObjectVariableInfo(inputOvi.tag, outputType);
		}
	}

	//

	private int transform(MutableConstantPool constants) throws JakartaTransformException {
		int modifiedConstants = 0;

		int numConstants = constants.size();
		for ( int constantNo = 1; constantNo < numConstants; constantNo++ ) {
			switch ( constants.tag(constantNo) ) {
				case ConstantPool.CONSTANT_Class: {
					ClassInfo info = constants.entry(constantNo);
					String inputClassName = constants.entry(info.class_index);
					String outputClassName = transformBinaryType(inputClassName);
					if ( outputClassName != null ) {
						constants.entry( constantNo, new ClassInfo(constants.utf8Info(outputClassName)) );
						modifiedConstants++;
						verbose("    Class: %s -> %s\n", inputClassName, outputClassName);
					}
					break;
				}

				case ConstantPool.CONSTANT_NameAndType: {
					NameAndTypeInfo info = constants.entry(constantNo);
					String inputDescriptor = constants.utf8(info.descriptor_index);
					String outputDescriptor = transformDescriptor(inputDescriptor);
					if ( outputDescriptor != null ) {
						constants.entry(constantNo,
							new NameAndTypeInfo( info.name_index, constants.utf8Info(outputDescriptor)) );
						modifiedConstants++;
						verbose("    NameAndType: %s -> %s\n", inputDescriptor, outputDescriptor);
					}
					break;
				}

				case ConstantPool.CONSTANT_MethodType: {
					MethodTypeInfo info = constants.entry(constantNo);
					String inputDescriptor = constants.utf8(info.descriptor_index);
					String outputDescriptor = transformDescriptor(inputDescriptor);
					if ( outputDescriptor != null ) {
						constants.entry( constantNo, new MethodTypeInfo(constants.utf8Info(outputDescriptor)) );
						modifiedConstants++;
						verbose("    MethodType: %s -> %s\n", inputDescriptor, outputDescriptor);
					}
					break;
				}

				case ConstantPool.CONSTANT_String: {
					// TODO maybe rewrite String constants for renamed packages?

					StringInfo info = constants.entry(constantNo);
					String utf8 = constants.utf8(info.string_index);
					if ( utf8.contains("javax") ) {
						verbose("    #%s = String #%s [uses-javax] // %s\n", constantNo, info.string_index, utf8);
					}
					break;
				}

				case ConstantPool.CONSTANT_Fieldref:
				case ConstantPool.CONSTANT_Methodref:
				case ConstantPool.CONSTANT_InterfaceMethodref:
				case ConstantPool.CONSTANT_MethodHandle:
				case ConstantPool.CONSTANT_Dynamic:
				case ConstantPool.CONSTANT_InvokeDynamic:
				case ConstantPool.CONSTANT_Module:
				case ConstantPool.CONSTANT_Package:
				case ConstantPool.CONSTANT_Utf8:
				case ConstantPool.CONSTANT_Integer:
				case ConstantPool.CONSTANT_Float:
					break;

				case ConstantPool.CONSTANT_Long:
				case ConstantPool.CONSTANT_Double:
					// For some insane optimization reason, the Long(5) and Double(6)
					// entries take two slots in the constant pool.  See 4.4.5
					constantNo++;
					break;

				default:
					throw new JakartaTransformException(
						"Unrecognized constant pool entry [ " + constantNo + " ]:" +
						" [ " + constants.entry(constantNo) + " ]");
			}
		}

		return modifiedConstants;
	}
}

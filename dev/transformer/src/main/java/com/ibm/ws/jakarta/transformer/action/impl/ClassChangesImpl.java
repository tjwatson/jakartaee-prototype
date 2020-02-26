package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.ClassChanges;

public class ClassChangesImpl extends ChangesImpl implements ClassChanges {
	@Override
	public void clearChanges() {
		inputClassName = null;
		outputClassName = null;

		inputSuperName = null;
		outputSuperName = null;

		modifiedInterfaces = 0;

		modifiedFields = 0;
		modifiedMethods = 0;
		modifiedAttributes = 0;

		modifiedConstants = 0;
	}

	@Override
	public boolean hasNonResourceNameChanges() {
		return ( ((inputClassName != null) && (outputClassName != null) && !inputClassName.equals(outputClassName)) ||
				 ((inputSuperName != null) && (outputSuperName != null) && !inputSuperName.equals(outputSuperName)) ||

				 (modifiedInterfaces > 0) ||

				 (modifiedFields > 0) ||
				 (modifiedMethods > 0) ||
				 (modifiedAttributes > 0) ||

				 (modifiedConstants > 0) );
	}

	//

	private String inputClassName;
	private String outputClassName;

	@Override
	public String getInputClassName() {
		return inputClassName;
	}

	@Override
	public void setInputClassName(String inputClassName) {
		this.inputClassName = inputClassName;
	}

	@Override
	public String getOutputClassName() {
		return outputClassName;
	}

	@Override
	public void setOutputClassName(String outputClassName) {
		this.outputClassName = outputClassName;
	}

	//

	private String inputSuperName;
	private String outputSuperName;

	@Override
	public String getInputSuperName() {
		return inputSuperName;
	}

	@Override
	public void setInputSuperName(String inputSuperName) {
		this.inputSuperName = inputSuperName;
	}

	@Override
	public String getOutputSuperName() {
		return outputSuperName;
	}

	@Override
	public void setOutputSuperName(String outputSuperName) {
		this.outputSuperName = outputSuperName;
	}

	private int modifiedInterfaces;

	@Override
	public int getModifiedInterfaces() {
		return modifiedInterfaces;
	}

	@Override
	public void setModifiedInterfaces(int modifiedInterfaces) {
		this.modifiedInterfaces = modifiedInterfaces;
	}

	@Override
	public void addModifiedInterface() {
		modifiedInterfaces++;
		
	}

	//

	private int modifiedFields;
	private int modifiedMethods;
	private int modifiedAttributes;

	@Override
	public int getModifiedFields() {
		return modifiedFields;
	}

	@Override
	public void setModifiedFields(int modifiedFields) {
		this.modifiedFields = modifiedFields;
	}

	@Override
	public void addModifiedField() {
		modifiedFields++;
	}

	@Override
	public int getModifiedMethods() {
		return modifiedMethods;
	}

	@Override
	public void setModifiedMethods(int modifiedMethods) {
		this.modifiedMethods = modifiedMethods;
	}

	@Override
	public void addModifiedMethod() {
		modifiedMethods++;
	}

	@Override
	public int getModifiedAttributes() {
		return modifiedAttributes;
	}

	@Override
	public void setModifiedAttributes(int modifiedAttributes) {
		this.modifiedAttributes = modifiedAttributes;
	}

	@Override
	public void addModifiedAttribute() {
		modifiedAttributes++;
	}

	//

	private int modifiedConstants;

	@Override
	public int getModifiedConstants() {
		return modifiedConstants;
	}

	@Override
	public void setModifiedConstants(int modifiedConstants) {
		this.modifiedConstants = modifiedConstants;
	}

	@Override
	public void addModifiedConstant() {
		modifiedConstants++;
	}
	
	//
	
	@Override
	public void displayChanges(PrintStream printStream, String inputPath, String outputPath) {
		printStream.printf(
			"Input name [ %s ] as [ %s ]\n",
			getInputResourceName(), inputPath );
		
		printStream.printf(
			"Output name [ %s ] as [ %s ]\n",
			getOutputResourceName(), outputPath );

		printStream.printf( "Class name [ %s ] [ %s ]\n",
			getInputClassName(),
			getOutputClassName() );

		String useInputSuperName = getInputSuperName();
		if ( useInputSuperName != null ) {
			printStream.printf( "Super class name [ %s ] [ %s ]\n",
				useInputSuperName,
				getOutputSuperName() );
		}

		printStream.printf( "Modified interfaces [ %s ]\n", getModifiedInterfaces() );
		printStream.printf( "Modified fields     [ %s ]\n", getModifiedFields() );
		printStream.printf( "Modified methods    [ %s ]\n", getModifiedMethods() );
		printStream.printf( "Modified constants  [ %s ]\n", getModifiedConstants() );	
	}
}

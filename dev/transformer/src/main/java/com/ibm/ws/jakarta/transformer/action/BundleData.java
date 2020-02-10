package com.ibm.ws.jakarta.transformer.action;

public interface BundleData {
    char ADDITIVE_CHAR = '+';
    char QUOTE_CHAR = '"';
    char COMMA_CHAR = ',';

	String getSymbolicName();
	String getVersion();

	boolean getAddName();
	String getName();

	boolean getAddDescription();
	String getDescription();

	String updateName(String initialName);
	String updateDescription(String initialDescription);
}

package com.ibm.ws.jakarta.transformer.action;

public enum ActionType {
	NULL,

	CLASS,
	MANIFEST, FEATURE,
	SERVICE_CONFIG,
	XML,

	ZIP, JAR, WAR, RAR, EAR,
	JAVA,
	DIRECTORY;

	public boolean matches(String tag) {
		return name().toLowerCase().startsWith(tag);
	}
}

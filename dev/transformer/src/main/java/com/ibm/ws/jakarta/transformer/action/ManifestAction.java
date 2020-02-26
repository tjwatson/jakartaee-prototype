package com.ibm.ws.jakarta.transformer.action;

public interface ManifestAction extends Action {
	String META_INF = "META-INF/";
	String MANIFEST_MF= "MANIFEST.MF";
	String META_INF_MANIFEST_MF = "META-INF/MANIFEST.MF";

	@Override
	ManifestChanges getChanges();

	public boolean getIsManifest();
	public boolean getIsFeature();

	public String quote(StringBuilder sb, String value);
}

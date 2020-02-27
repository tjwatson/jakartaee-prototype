package com.ibm.ws.jakarta.transformer.action;

import java.io.File;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;

public interface JavaAction extends Action {

	@Override
	JavaChanges getChanges();
	
    void apply(File inputFile, File outputFile)  throws JakartaTransformException;
}

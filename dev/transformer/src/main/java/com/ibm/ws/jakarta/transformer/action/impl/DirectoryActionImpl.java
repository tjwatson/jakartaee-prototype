package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.File;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.ContainerChanges;
import com.ibm.ws.jakarta.transformer.action.DirectoryAction;

public class DirectoryActionImpl extends ContainerActionImpl implements DirectoryAction {

    public DirectoryActionImpl(LoggerImpl logger,
                               InputBufferImpl buffer,
                               SelectionRuleImpl selectionRule,
                               SignatureRuleImpl signatureRule) {

            super(logger, buffer, selectionRule, signatureRule);
    }

	//

	@Override
	public ActionType getActionType() {
		return ActionType.DIRECTORY;
	}

	@Override
	public String getName() {
		return "Directory Action";
	}

	//

	@Override
	protected DirectoryChangesImpl newChanges() {
		return new DirectoryChangesImpl();
	}

	@Override
	public DirectoryChangesImpl getChanges() {
		return (DirectoryChangesImpl) super.getChanges();
	}

	//

	@Override
	public boolean accept(String resourceName, File resourceFile) {
		return ( (resourceFile != null) && resourceFile.isDirectory() );
	}

    @Override
	public void apply(String inputPath, File inputFile, File outputFile)
		throws JakartaTransformException {

	    setResourceNames(inputPath, inputPath);
        transform(".", inputFile, outputFile);
	}

	protected void transform(
		String inputPath, File inputFile,
		File outputFile)  throws JakartaTransformException {

	    inputPath = inputPath + '/' + inputFile.getName();

	    if ( inputFile.isDirectory() ) {
	    	if ( !outputFile.exists() ) {
	    		outputFile.mkdir();
	    	}

	    	for ( File childInputFile : inputFile.listFiles() ) {
	    		File childOutputFile = new File( outputFile, childInputFile.getName() );
	    		transform(inputPath, childInputFile, childOutputFile);
	    	}

	    } else {
	    	Action selectedAction = acceptAction(inputPath, inputFile);
	    	if ( selectedAction == null ) {
	    		recordUnaccepted(inputPath);
	    	} else if ( !select(inputPath) ) {
	    		recordUnselected(selectedAction, !ContainerChanges.HAS_CHANGES, inputPath);
	    	} else {
	    		selectedAction.apply(inputPath, inputFile, outputFile);
	    		recordTransform(selectedAction, inputPath);
	    	}
	    }
	}
}

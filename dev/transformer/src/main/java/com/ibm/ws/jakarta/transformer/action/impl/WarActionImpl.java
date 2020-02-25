package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.InputStream;
import java.io.OutputStream;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.WarAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;

public class WarActionImpl extends ContainerActionImpl implements WarAction {
	public WarActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);
	}

	//

	public String getName() {
		return "WAR Action";
	}

	@Override
	public ActionType getActionType() {
		return ActionType.WAR;
	}

	//

	@Override
	protected WarChangesImpl newChanges() {
		return new WarChangesImpl();
	}

	@Override
	public WarChangesImpl getChanges() {
		return (WarChangesImpl) super.getChanges();
	}

	//

	@Override
	public String getAcceptExtension() {
		return ".war";
	}

	// WAR do not support byte based conversion

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength) throws JakartaTransformException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void apply(String inputPath, InputStream inputStream, String outputPath, OutputStream outputStream)
			throws JakartaTransformException {
		// TODO
	}
}
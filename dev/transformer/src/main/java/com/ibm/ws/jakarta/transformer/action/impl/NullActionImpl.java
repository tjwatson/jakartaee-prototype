package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.File;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.NullAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;

public class NullActionImpl extends ActionImpl implements NullAction {

	public NullActionImpl(
			LoggerImpl logger,
			InputBufferImpl buffer,
			SelectionRuleImpl selectionRule,
			SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);
	}

	//

	public String getName() {
		return "Null Action";
	}

	@Override
	public ActionType getActionType() {
		return ActionType.NULL;
	}

	//

	@Override
	protected NullChangesImpl newChanges() {
		return new NullChangesImpl();
	}

	@Override
	public NullChangesImpl getChanges() {
		return (NullChangesImpl) super.getChanges();
	}

	//

	@Override
	public String getAcceptExtension() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean accept(String resourcePath, File resourceFile) {
		return true;
	}

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength)
		throws JakartaTransformException {

		clearChanges();
		setResourceNames(inputName, inputName);

		return new ByteData(inputName, inputBytes, 0, inputLength);
	}
}

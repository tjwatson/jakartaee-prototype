package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.CompositeAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;

public class CompositeActionImpl extends ActionImpl implements CompositeAction {

	public <A extends ActionImpl> A addUsing(ActionInit<A> init) {
		A action = createUsing(init);
		addAction(action);
		return action;
	}

	public CompositeActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);

		this.actions = new ArrayList<ActionImpl>();
		this.acceptedAction = null;
	}

	//

	@Override
	public String getName() {
		return ( (acceptedAction == null) ? null : acceptedAction.getName() );
	}

	@Override
	public ActionType getActionType() {
		return ( (acceptedAction == null) ? null : acceptedAction.getActionType() );
	}

	@Override
	public ChangesImpl getChanges() {
		return ( (acceptedAction == null) ? null : acceptedAction.getChanges() );
	}

	@Override
	protected ChangesImpl newChanges() {
		// Invoked by 'ActionImpl.init(): A return value must be provided.
		return null;
	}

	//

	private final List<ActionImpl> actions;
	private ActionImpl acceptedAction;

	@Override
	public List<ActionImpl> getActions() {
		return actions;
	}

	protected void addAction(ActionImpl action) {
		getActions().add(action);
	}

	@Override
	public String getAcceptExtension() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ActionImpl acceptAction(String resourceName, File resourceFile) {
		for ( ActionImpl action : getActions() ) {
			if ( action.accept(resourceName, resourceFile) ) {
				acceptedAction = action;
				return action;
			}
		}
		acceptedAction = null;
		return null;
	}

	@Override
	public boolean accept(String resourceName, File resourceFile) {
		return ( acceptAction(resourceName, resourceFile) != null );
	}

	@Override
	public ActionImpl getAcceptedAction() {
		return ( (acceptedAction == null) ? null : acceptedAction );
	}

	//

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength)
		throws JakartaTransformException {

		return getAcceptedAction().apply(inputName, inputBytes, inputLength);
	}
}

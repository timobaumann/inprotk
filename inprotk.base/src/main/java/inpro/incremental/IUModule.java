package inpro.incremental;

import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.EditType;
import inpro.incremental.unit.IU;
import inpro.incremental.unit.IUList;
import inpro.util.TimeUtil;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Abstract class of an incremental module in InproTk.
 * Implementing modules should implement leftBufferUpdate() and,
 * from within this method, use one of the rightBuffer.setBuffer() methods
 * to set their output after this processing increment.
 * 
 * @author timo
 */
public abstract class IUModule extends PushBuffer {

	public List<PushBuffer> iulisteners;
	
    protected Logger logger;
    
	/** the right buffer of this module */
	protected final RightBuffer rightBuffer = new RightBuffer();
	
	public IUModule() {
		logger = LogManager.getLogger(this.getClass());
	}
	
	public void addListener(PushBuffer listener) {
		if (iulisteners == null) {
			iulisteners = new ArrayList<PushBuffer>();
		}
		iulisteners.add(listener);
	}
	
	/**
	 * the method that IU modules must implement 
	 * @param ius list of IUs that make up the current hypothesis
	 * @param edits a list of edits since the last call
	 */
	protected abstract void leftBufferUpdate(Collection<? extends IU> ius,
			List<? extends EditMessage<? extends IU>> edits);

	@Override
	public synchronized void hypChange(Collection<? extends IU> ius,
			List<? extends EditMessage<? extends IU>> edits) {
		leftBufferUpdate(ius, edits);
		rightBuffer.notify(iulisteners);
	}

	/* * * utility methods * * */
	
	public int getTime() {
		return (int) (System.currentTimeMillis() - TimeUtil.startupTime);
	}
	
	public void notifyListeners() {
		rightBuffer.notify(iulisteners);
	}
	
	/**
	 * Encapsulates the module's output in two representations. 
	 * 
	 * Modules should call either of the setBuffer methods in their 
	 * implementation of leftBufferUpdate().
	 * 
	 * @author timo
	 */
	protected class RightBuffer {
		/** true if the content has changed since the last call to notify */
		boolean hasUpdates = false;
		IUList<IU> ius = new IUList<IU>();
		List<EditMessage<IU>> edits = new ArrayList<EditMessage<IU>>();
		
		public List<IU> getBuffer() {
			return new ArrayList<IU>(ius);
		}
		
		/** just a list of IUs, automatically infers the edits since the last call */ 
		public void setBuffer(Collection<? extends IU> outputIUs) {
			IUList<IU> newList = new IUList<IU>();
			if (outputIUs != null) 
				newList.addAll(outputIUs);
			edits = ius.diff(newList);
			ius = newList;
			hasUpdates = !edits.isEmpty();
		}
		
		/** just a list of edits, automatically updates IUs from last call */
		@SuppressWarnings("unchecked")
		public void setBuffer(List<? extends EditMessage<? extends IU>> edits) {
			assert (edits != null);
			ius.apply((List<EditMessage<IU>>) edits);
			this.edits = (List<EditMessage<IU>>) edits;
			hasUpdates = !edits.isEmpty();
		}
		
		/** both ius and edits */
		@SuppressWarnings("unchecked")
		public void setBuffer(Collection<? extends IU> outputIUs,
				List<? extends EditMessage<? extends IU>> outputEdits) {
			ius = new IUList<IU>();
			if (outputIUs != null)
				ius.addAll(outputIUs);
			edits = new ArrayList<EditMessage<IU>>();
			if (outputEdits != null) 
				edits.addAll((List<EditMessage<IU>>)outputEdits);
			hasUpdates = !edits.isEmpty();
		}
		
		/** 
		 * add a single IU to the output buffer.
		 * automatically generatees the appropriate edit message 
		 */
		public void addToBuffer(IU iu) {
			ius.add(iu);
			edits.add(new EditMessage<IU>(EditType.ADD, iu));
			hasUpdates = true;
		}
		
		/** 
		 * add a single IU to the output buffer.
		 * automatically generatees the appropriate edit message and 
		 * attaches the new IU to the previously last in the buffer. 
		 */
		public void addToBufferSetSLL(IU iu) { // NO_UCD (unused code): this should be tested as it is part of our public API
			iu.setSameLevelLink(ius.getLast());
			addToBuffer(iu);
		}
		
		/**
		 * apply the given EditMessage to the output buffer.
		 */
		public void editBuffer(EditMessage<IU> edit) {
			ius.apply(edit);
			edits.add(edit);
			hasUpdates = true;
		}
		
		public void clearBuffer() { // NO_UCD (unused code): this should be tested as it is part of our public API
			setBuffer(Collections.emptyList(), Collections.<EditMessage<IU>>emptyList());
		}	

		public void notify(PushBuffer listener) {
			if (hasUpdates) {
				listener.hypChange(ius, edits);
			}
		}
		
		public void notify(List<PushBuffer> listeners) {
			if (listeners == null)
				return;
			for (PushBuffer listener : listeners) {
				notify(listener);
			}
			edits.clear();
			hasUpdates = false;
		}
	}
	
}

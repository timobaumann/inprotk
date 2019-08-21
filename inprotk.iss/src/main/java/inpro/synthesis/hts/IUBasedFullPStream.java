package inpro.synthesis.hts;

import inpro.incremental.unit.IU;
import inpro.incremental.unit.SysSegmentIU;

public class IUBasedFullPStream extends FullPStream {

    SysSegmentIU currIU;
    final SysSegmentIU firstIU;
    int currIUFrameOffset;
    	
    public IUBasedFullPStream(IU firstIU) {
    	while (firstIU != null && !(firstIU instanceof SysSegmentIU)) {
    		firstIU = firstIU.groundedIn().get(0);
    	}
    	if (firstIU == null) {
    		throw new IllegalArgumentException("the IU you gave me does not ground in a SysSegmentIU!");
    	}
    	this.firstIU = (SysSegmentIU) firstIU;
    	this.reset();
    }

    public void reset() {
    	this.currIU = this.firstIU;
    	currIUFrameOffset = 0;
    	super.reset();
    }
    
    @Override
	public int getMaxT() {
    	// equivalent to 100 ms
        return 20;
    	//return getTrueLength();
    }
    
    @Override
    public boolean hasNextFrame() {
    	boolean returnValue = currIU != null && ((currPosition < currIUFrameOffset + currIU.durationInSynFrames()) 
    						     || currIU.getNextSameLevelLink() != null);
/*    	if (!returnValue) {
    		System.err.println("no more frames");
    		System.err.println(currIU.deepToString());
    	} */
    	return returnValue;
    }
    
    @Override
    public FullPFeatureFrame getNextFrame() {
    	while (currIU != null && currPosition >= currIUFrameOffset + currIU.durationInSynFrames()) {
    		currIUFrameOffset += currIU.durationInSynFrames();
    		currIU = (SysSegmentIU) currIU.getNextSameLevelLink();
    	}
    	int iuLocalPosition = currPosition - currIUFrameOffset;
		currPosition++;
    	return currIU != null ? currIU.getHMMSynthesisFrame(iuLocalPosition) : null;
    }
    
    @Override
	public FullPFeatureFrame getFullFrame(int t) {
    	setNextFrame(t);
    	return getNextFrame();
    }
    
}

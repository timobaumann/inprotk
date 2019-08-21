package inpro.synthesis.hts;

import marytts.htsengine.HTSPStream;

/**
 * A stream of all Parameter Features ordered by time necessary for the vocoder.
 * i.e., the data that Mary/HTS stores in paramtypes->time ordering re-organized for time->paramtypes access,
 * (with paramtypes conveniently wrapped in  FullPFeatureFrames)
 * which, surprise!, allows for incremental production of parameter features.
 * @author timo
 */
public abstract class FullPStream {
	
	public static int FRAMES_PER_SECOND = 200;

	/** the current position in the feature stream */
    int currPosition = 0;
    
    public abstract FullPFeatureFrame getFullFrame(int t);
    
    public FullPFeatureFrame getNextFrame() {
    	return getFullFrame(currPosition++);
    }
    
	public boolean hasNextFrame() {
		return hasFrame(currPosition);
	}
	
	public void setNextFrame(int newPosition) {
	//	assert hasFrame(newPosition);
		currPosition = newPosition;
	}
	
	public int getMcepParSize() { return getNextFrame().getMcepParSize(); }
	public int getMcepVSize() { return getMcepParSize() * HTSPStream.NUM; }
	public int getStrParSize() { return getNextFrame().getStrParSize(); }
    
	public boolean hasFrame(int t) {
		return t < getMaxT();
	}
	
	public String deepToString() { // NO_UCD (unused code): extended toString methods are generally a good thing to keep around
		StringBuilder sb = new StringBuilder("FullPStream:\n");
		int oldPos = currPosition;
		setNextFrame(0);
		while (hasNextFrame()) {
			sb.append("frame ");
			sb.append(currPosition);
			sb.append(": ");
			sb.append(getNextFrame().toString());
			sb.append("\n");
		}
		currPosition = oldPos;
		return sb.toString();
	}
	
	public void reset() {
		currPosition = 0;
	}
	
    public abstract int getMaxT();
}

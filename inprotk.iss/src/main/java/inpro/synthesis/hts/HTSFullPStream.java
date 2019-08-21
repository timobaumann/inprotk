package inpro.synthesis.hts;

import marytts.htsengine.HTSPStream;
import marytts.htsengine.HTSParameterGeneration;

public class HTSFullPStream extends FullPStream {
    private HTSPStream mcepPst;
    private HTSPStream strPst;
    private HTSPStream magPst;
    private HTSPStream lf0Pst;
    private int[] lf0indices; 
    private boolean[] voiced;
    
    public HTSFullPStream(HTSParameterGeneration htspg) {
    	this(htspg.getMcepPst(), htspg.getStrPst(), htspg.getMagPst(), htspg.getlf0Pst(), htspg.getVoicedArray());
    }
    
    public HTSFullPStream(HTSPStream mcepPst, HTSPStream strPst, HTSPStream magPst, HTSPStream lf0Pst, boolean[] voiced) {
        this.mcepPst = mcepPst;
        this.strPst = strPst;
        try {
			this.magPst = magPst != null ? magPst : new EmptyHTSPStream();
	        this.lf0Pst = lf0Pst != null ? lf0Pst : new EmptyHTSPStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
        this.voiced = voiced;
        if (voiced != null && lf0Pst != null) {
	        lf0indices = new int[voiced.length];
	        for (int t = 0, vi = -1; t < voiced.length; t++) {
	            if (voiced[t]) // && vi < lf0Pst.getT() - 1) # this check shouldn't be necessary at all!
	                vi++;
	            lf0indices[t] = vi;
	            assert vi <= lf0Pst.getT();
	        }
        }
    }
    
    @Override
	public FullPFeatureFrame getFullFrame(int t) {
    	//assert t < getMaxT();
    	if (t < getMaxT()) {
    		return new FullPFeatureFrame(mcepPst.getParVec(t), 
    									 magPst.getParVec(t), 
    									 strPst.getParVec(t),
                    voiced != null && voiced[t],
    									 (lf0indices != null && lf0indices[t] >= 0) ? lf0Pst.getPar(lf0indices[t], 0) : 0);
    	} else return null;
    }
    
    @Override
	public int getMcepParSize() {
        return mcepPst.getOrder();
    }

    @Override
	public int getStrParSize() {
        return strPst.getOrder();
    }

    @Override
	public int getMaxT() {
        return mcepPst.getT();
    }
    
    class EmptyHTSPStream extends HTSPStream {

    	private double[] emptyParVec = new double[] {};
    	
		public EmptyHTSPStream() throws Exception {
			super(0, 0, null, 0);
		}
		
		@Override
		public double[] getParVec(int t) {
			return emptyParVec;
		}
		
		public double getPar(int i, int j) {
		    return 0;
		}
    	
    }

}

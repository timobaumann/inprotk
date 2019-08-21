package inpro.synthesis.hts;

import marytts.features.FeatureVector;
import marytts.htsengine.HMMData;
import marytts.htsengine.HTSModel;

public class SynthesisPayload {
	FeatureVector fv;
	HTSModel htsModel;
	HMMData hmmdata;
	public SynthesisPayload(FeatureVector fv, HTSModel htsModel, HMMData hmmdata) {
		this.fv = fv;
		this.htsModel = htsModel;					
		this.hmmdata = hmmdata;
	}
	@Override
	public String toString() {
		return "synthesis payload for " + htsModel.getPhoneName();
	}
	public FeatureVector getFeatureVector() { return fv; }
	public HTSModel getHTSModel() { return htsModel; }
	public HMMData getHMMData() { return hmmdata; }
}


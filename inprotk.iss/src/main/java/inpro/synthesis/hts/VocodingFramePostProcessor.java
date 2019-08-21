package inpro.synthesis.hts;

import inpro.incremental.unit.SysSegmentIU;

public interface VocodingFramePostProcessor {

	FullPFeatureFrame postProcess(SysSegmentIU sysSegmentIU, FullPFeatureFrame frame);
	
}

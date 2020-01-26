package inpro.synthesis;

import static org.junit.Assert.*;

import inpro.audio.DispatchStream;
import inpro.incremental.processor.AdaptableSynthesisModule;
import inpro.incremental.sink.LabelWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SynthesisModuleAdaptationUnitTest extends SynthesisModuleTestBase {
	
	@Before
	public void setupMinimalSynthesisEnvironment() {
        System.setProperty("inpro.tts.language", "de");
		System.setProperty("inpro.tts.voice", "bits1-hsmm");
		dispatcher = DispatchStream.drainingDispatchStream();
		myIUModule = new TestIUModule();
		asm = new AdaptableSynthesisModule(dispatcher);
		asm.addListener(new LabelWriter());
		myIUModule.addListener(asm);
	}

	@After
	public void waitForSynthesis() {
		dispatcher.waitUntilDone();
		myIUModule.reset();
	}

	/**
	 * test that scaling works as expected (scaling error is within 10%)
	 */
	@Test(timeout=60000)
	public void testScaleTempo() {
		String textKurz = "eins zwei drei vier fünf";
		// get the standard duration
		startChunk(textKurz);
		long timeBeforeSynthesis = System.currentTimeMillis();
		dispatcher.waitUntilDone();
		long timeForNormalSynthesis = System.currentTimeMillis() - timeBeforeSynthesis;
		// now mesure scaled synthesis
		double[] scalingFactors = {0.41, 0.51, 0.64, 0.8, 1.0, 1.25, 1.56, 1.95, 2.44};
		for (double scalingFactor : scalingFactors) {
			startChunk(textKurz);
			timeBeforeSynthesis = System.currentTimeMillis();
			asm.scaleTempo(scalingFactor);
			dispatcher.waitUntilDone();
			long timeForSlowSynthesis = System.currentTimeMillis() - timeBeforeSynthesis;
			double actualFactor = (double) timeForSlowSynthesis / (double) timeForNormalSynthesis;
			double deviationOfFactor = actualFactor / scalingFactor;
			assertTrue(Double.toString(actualFactor) + " but should have been " + Double.toString(scalingFactor), 
					deviationOfFactor < 1.1 && deviationOfFactor > 0.91);
		}
	}
	
}

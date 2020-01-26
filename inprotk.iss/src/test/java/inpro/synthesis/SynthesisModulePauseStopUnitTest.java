package inpro.synthesis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import inpro.audio.DispatchStream;
import inpro.incremental.processor.AdaptableSynthesisModule;
import inpro.incremental.sink.LabelWriter;
import inpro.incremental.unit.IU;
import inpro.incremental.unit.ChunkIU;
import inpro.incremental.unit.IU.IUUpdateListener;
import inpro.incremental.unit.IU.Progress;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SynthesisModulePauseStopUnitTest extends SynthesisModuleTestBase {

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

	@Test(timeout=60000)
	public void testPauseResumeAfterOngoingWord() throws InterruptedException {
		int initialDelay = 400;
		int pauseDuration = 1000;
		startChunk("eins zwei drei vier fünf sechs sieben acht neun zehn");
		Thread.sleep(initialDelay);
		asm.pauseAfterOngoingWord();
		Thread.sleep(pauseDuration);
		asm.resumePausedSynthesis();
	}
	
	@Test(timeout=60000)
	public void testPauseStopAfterOngoingWord() throws InterruptedException {
		int initialDelay = 400;
		int pauseDuration = 1000;
		startChunk("eins zwei drei vier fünf sechs sieben acht neun zehn");
		Thread.sleep(initialDelay);
		asm.pauseAfterOngoingWord();
		Thread.sleep(pauseDuration);
		asm.stopAfterOngoingWord();
	}
	
	/**
	 * assert that aborting after the ongoing phoneme does not take longer than 600 ms 
	 * (this test uses digits, which should not last much longer than 600 ms to say) 
	 * in addition, assert that aborting something that has already ended does not fail
	 */
	@Test(timeout=60000)
	public void testStopAfterOngoingWord() throws InterruptedException {
		for (int initialDelay = 300; initialDelay < 4000; initialDelay += 300) {
			startChunk("eins zwei drei vier fünf sechs sieben acht neun zehn");
			Thread.sleep(initialDelay);
			long timeBeforeAbort = System.currentTimeMillis();
			asm.stopAfterOngoingWord();
			dispatcher.waitUntilDone();
			long timeUntilAbort = System.currentTimeMillis() - timeBeforeAbort;
			assertTrue(Long.toString(timeUntilAbort), timeUntilAbort < 600);
		}
	}

	/**
	 * assert that aborting after the ongoing phoneme does not take longer than 250 ms 
	 * in addition, assert that aborting something that has already ended does not fail
	 */
	@Test(timeout=60000)
	public void testStopAfterOngoingPhoneme() throws InterruptedException {
		for (int initialDelay = 300; initialDelay < 4000; initialDelay += 300) {
			startChunk("eins zwei drei vier fünf sechs sieben acht neun zehn");
			Thread.sleep(initialDelay);
			long timeBeforeAbort = System.currentTimeMillis();
			asm.stopAfterOngoingPhoneme();
			dispatcher.waitUntilDone();
			long timeUntilAbort = System.currentTimeMillis() - timeBeforeAbort;
			assertTrue(Long.toString(timeUntilAbort), timeUntilAbort < 250);
		}
	}
	
	/**
	 * assert that stopping results in the underlying chunkIUs being, well, what?
	 */
	@Test(timeout=60000)
	public void testChunkIUProgressOnStop() throws InterruptedException {
		int[] delay = { 100, 200, 1500, 2100, 3500 };
		// we expect the first chunk to be ongoing for 300 and 700 ms, and to have completed after 2100 ms
		Progress[][] expectedProgressOfFirstChunk = { { Progress.ONGOING }, 
											   { Progress.ONGOING }, 
											   { Progress.ONGOING, Progress.COMPLETED }, 
											   { Progress.ONGOING, Progress.COMPLETED }, 
											   { Progress.ONGOING, Progress.COMPLETED }, 
											 };
		// we expect the second chunk to only start after more than 700 ms, and to be completed before 3500 ms
		Progress[][] expectedProgressOfSecondChunk = { 
												{ },
										        { },
										        { Progress.UPCOMING, Progress.ONGOING },										       
										        { Progress.UPCOMING, Progress.ONGOING },										       
										        { Progress.UPCOMING, Progress.ONGOING, Progress.COMPLETED },										       
											  };
		for (int i = 0; i < delay.length; i++) {
			ChunkIU firstChunk = new ChunkIU("Ein ganz besonders langer"); // takes ~ 870 ms
			final List<Progress> firstChunkProgressUpdates = new ArrayList<Progress>();
			firstChunk.addUpdateListener(new IUUpdateListener() {@Override
				public void update(IU updatedIU) {
					firstChunkProgressUpdates.add(updatedIU.getProgress());
				}});
			ChunkIU secondChunk = new ChunkIU("und sehr komplizierter Satz."); // full utterances takes ~2700 ms
			final List<Progress> secondChunkProgressUpdates = new ArrayList<Progress>();
			secondChunk.addUpdateListener(new IUUpdateListener() {@Override
				public void update(IU updatedIU) {
					secondChunkProgressUpdates.add(updatedIU.getProgress());
				}});
			myIUModule.addIUAndUpdate(firstChunk);
			myIUModule.addIUAndUpdate(secondChunk);
			Thread.sleep(delay[i]);
			asm.stopAfterOngoingWord();
			dispatcher.waitUntilDone();
			//System.err.println(firstChunkProgressUpdates.toString());
			assertArrayEquals("in round " + i + " first chunk updates: " + firstChunkProgressUpdates.toString(), 
					expectedProgressOfFirstChunk[i], 
					firstChunkProgressUpdates.toArray());
			assertArrayEquals("in round " + i + " second chunk updates: " + secondChunkProgressUpdates.toString(), 
					expectedProgressOfSecondChunk[i], 
					secondChunkProgressUpdates.toArray());
		}
	}

}

package ch.ethz.inf.vs.californium.test;

import static org.junit.Assert.assertArrayEquals;
import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.ethz.inf.vs.californium.coap.CoAP.OptionRegistry;
import ch.ethz.inf.vs.californium.coap.Option;
import ch.ethz.inf.vs.californium.coap.OptionSet;

/**
 * This test tests the class Option. We test that the conversion of String,
 * integer and long values to byte arrays work properly.
 */
public class OptionTest {

	@Before
	public void setupServer() {
		System.out.println("\nStart "+getClass().getSimpleName());
	}
	
	@After
	public void shutdownServer() {
		System.out.println("End "+getClass().getSimpleName());
	}
	
	@Test
	public void testSetValue() {
		Option option = new Option();

		option.setValue(new byte[4]);
		assertArrayEquals(option.getValue(), new byte[4]);
		
		option.setValue(new byte[] {69, -104, 35, 55, -104, 116, 35, -104});
		assertArrayEquals(option.getValue(), new byte[] {69, -104, 35, 55, -104, 116, 35, -104});
	}
	
	@Test
	public void testSetStringValue() {
		Option option = new Option();
		
		option.setStringValue("");
		assertArrayEquals(option.getValue(), new byte[0]);

		option.setStringValue("Californium");
		assertArrayEquals(option.getValue(), "Californium".getBytes());
	}
	
	@Test
	public void testSetIntegerValue() {
		Option option = new Option();

		option.setIntegerValue(0);
		assertArrayEquals(option.getValue(), new byte[0]);
		
		option.setIntegerValue(11);
		assertArrayEquals(option.getValue(), new byte[] {11});
		
		option.setIntegerValue(255);
		assertArrayEquals(option.getValue(), new byte[] { (byte) 255 });
		
		option.setIntegerValue(256);
		assertArrayEquals(option.getValue(), new byte[] {1, 0});
		
		option.setIntegerValue(18273);
		assertArrayEquals(option.getValue(), new byte[] {71, 97});
		
		option.setIntegerValue(1<<16);
		assertArrayEquals(option.getValue(), new byte[] {1, 0, 0});
		
		option.setIntegerValue(23984773);
		assertArrayEquals(option.getValue(), new byte[] {1, 109, (byte) 250, (byte) 133});
		
		option.setIntegerValue(0xFFFFFFFF);
		assertArrayEquals(option.getValue(), new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
	}
	
	@Test
	public void testSetLongValue() {
		Option option = new Option();

		option.setLongValue(0);
		assertArrayEquals(option.getValue(), new byte[0]);
		
		option.setLongValue(11);
		assertArrayEquals(option.getValue(), new byte[] {11});
		
		option.setLongValue(255);
		assertArrayEquals(option.getValue(), new byte[] { (byte) 255 });
		
		option.setLongValue(256);
		assertArrayEquals(option.getValue(), new byte[] {1, 0});
		
		option.setLongValue(18273);
		assertArrayEquals(option.getValue(), new byte[] {71, 97});
		
		option.setLongValue(1<<16);
		assertArrayEquals(option.getValue(), new byte[] {1, 0, 0});
		
		option.setLongValue(23984773);
		assertArrayEquals(option.getValue(), new byte[] {1, 109, (byte) 250, (byte) 133});

		option.setLongValue(0xFFFFFFFFL);
		assertArrayEquals(option.getValue(), new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
		
		option.setLongValue(0x9823749837239845L);
		assertArrayEquals(option.getValue(), new byte[] {-104, 35, 116, -104, 55, 35, -104, 69});
		
		option.setLongValue(0xFFFFFFFFFFFFFFFFL);
		assertArrayEquals(option.getValue(), new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
			(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
	}
	
	@Test
	public void testArbitraryOptions() {
		OptionSet options = new OptionSet();
		options.addETag(new byte[] {1, 2, 3});
		options.addLocationPath("abc");
		options.addOption(new Option(7));
		options.addOption(new Option(43));
		options.addOption(new Option(33));
		options.addOption(new Option(17));

		// Check that options are in the set
		Assert.assertTrue(options.hasOption(OptionRegistry.ETAG));
		Assert.assertTrue(options.hasOption(OptionRegistry.LOCATION_PATH));
		Assert.assertTrue(options.hasOption(7));
		Assert.assertTrue(options.hasOption(17));
		Assert.assertTrue(options.hasOption(33));
		Assert.assertTrue(options.hasOption(43));
		
		// Check that others are not
		Assert.assertFalse(options.hasOption(19));
		Assert.assertFalse(options.hasOption(53));
		
		// Check that we can remove options
		options.clearETags();
		Assert.assertFalse(options.hasOption(OptionRegistry.ETAG));
	}
}

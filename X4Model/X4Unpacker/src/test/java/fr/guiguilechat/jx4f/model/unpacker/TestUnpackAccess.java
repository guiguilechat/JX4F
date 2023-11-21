package fr.guiguilechat.jx4f.model.unpacker;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestUnpackAccess {

	@Test
	public void testInstanceFiles() {
		Assert.assertFalse(X4Cache.INSTANCE.mainData().isEmpty(), "could not load main data from the unpacker");
	}

}

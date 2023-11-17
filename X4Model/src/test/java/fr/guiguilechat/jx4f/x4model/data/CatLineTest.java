package fr.guiguilechat.jx4f.x4model.data;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CatLineTest {

	@Test
	public void testof() {
		CatLine test = CatLine.of("a b c.d 12 0000 xyz");
		Assert.assertEquals(test.path(), "a b c.d");
		Assert.assertEquals(test.bytes(), 12l);
		Assert.assertEquals(test.epoch(), 0l);
		Assert.assertEquals(test.hash(), "xyz");
	}

}

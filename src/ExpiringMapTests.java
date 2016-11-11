import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.core.Is.*;
import static org.hamcrest.CoreMatchers.nullValue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.junit.Test;

public class ExpiringMapTests {
	
	
	@Test (expected = IllegalArgumentException.class)
	public void shouldThrowExceptionForInvalidTTLs() {
		new ExpiringMap<String, String>(-23);
	}
	
	@Test
	public void shouldGetAndPutValues() {
		
		ExpiringMap<String, String> map = new ExpiringMap<String, String>(100000);
		
		map.put("apple", "good");
		map.put("pineapple", "great");
		map.put("papaya", "meh");
		
		assertThat(map.get("apple"), is("good"));
		assertThat(map.get("pineapple"), is("great"));
		assertThat(map.get("papaya"), is("meh"));
		
	}
	
	@Test
	public void shouldRemoveEntries() {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(10000);
		map.put("abcde", "bababa");
		
		map.remove("abcde");
		
		assertThat(map.get("abcde"), is(nullValue()));
		
	}
	
	@Test 
	public void shouldHandleLargeTTLs() {
	
		ExpiringMap<String, String> map = new ExpiringMap<>(999999999);
		
		map.put("abc", "val");
		
		assertThat(map.get("abc"), is("val"));
		
	}
	
	@Test
	public void shouldNotEvictEntriesWithStillTimeToLive() throws InterruptedException {
		
		int input = 1000;
		ExpiringMap<String, String> map = new ExpiringMap<>(input);
		map.put("k", "v");
		
		input += MILLISECONDS.toMillis(500);
		
		Thread.sleep(100);
		
		assertThat(map.get("k"), is("v"));
		
	}
	
	@Test
	public void shouldOnlyEvictEntriesThatHaveExpired() throws InterruptedException {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		map.put("k1", "val1");
		
		int time = 0;
		while(time++ < 2000){
			Thread.sleep(1);
			if(time == 1500) map.put("k2", "val2");
		}
		
		assertThat(map.get("k1"), is(nullValue()));
		assertThat(map.get("k2"), is("val2"));
		
	}
	
	@Test
	public void shouldExpireALargeNumberOfEntries() throws InterruptedException {
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		for(int i = 0; i < 500; i++) {
			String key = new BigInteger(130, new Random()).toString(32);
			String val = new BigInteger(130, new Random()).toString(32);
			map.put(key, val);
		}
		
		Thread.sleep(10000);
		
		assertThat(map.size(), is(0));
		
	}
	
	@Test 
	public void shouldReturnACollectionOfValues() {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(10000);
		for(int i = 0; i < 10; i++) {
			String key = new BigInteger(130, new Random()).toString(128);
			String val = new BigInteger(130, new Random()).toString(128);
			map.put(key, val);
		}
		
		Collection<String> values = map.values();
		
		assertThat(values.size(), is(10));
	}
	
	@Test
	public void shouldReturnTheCorrectSetOfKeys() throws InterruptedException {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(10000);
		map.put("key1", "val1");
		map.put("key2", "val2");
		map.put("key3", "val3");
		
		Set<String> set = map.keySet();
		
		assertThat(set.size(), is(3));
		assertThat(set.contains("key1"), is(true));
		assertThat(set.contains("key2"), is(true));
		assertThat(set.contains("key3"), is(true));

	}
	
	@Test
	public void shouldReturnCorrectlyForIsEmptyMap() throws InterruptedException {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		map.put("key1", "abc");
		
		assertThat(map.isEmpty(), is(false));
		
		map.remove("key1");
		
		assertThat(map.isEmpty(), is(true));
	}
	
	@Test
	public void shouldUpdateValueForAKeyThatAlreadyExists() throws InterruptedException {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		map.put("key1", "val1");
		
		map.put("key1", "val2");
		
		assertThat(map.get("key1"), is("val2"));
		assertThat(map.size(), is(1));
		
	}
	
	@Test
	public void shouldPutAnotherMapIntoTheExpiringMap() throws InterruptedException {
		
		Map<String, String> otherMap = new HashMap<>();
		otherMap.put("key1", "val1");
		otherMap.put("key2", "val2");
		
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		map.put("key3", "val3");
		
		map.putAll(otherMap);
		
		assertThat(map.size(), is(3));
		assertThat(map.get("key1"), is("val1"));
		assertThat(map.get("key2"), is("val2"));
		assertThat(map.get("key3"), is("val3"));
		
	}
	
	@Test 
	public void shouldClearTheExpiringManEntires() throws InterruptedException {
		
		ExpiringMap<String, String> map = new ExpiringMap<>(1000);
		map.put("key1", "val1");
		map.put("key2", "val2");
		
		map.clear();
		
		assertThat(map.size(), is(0));
		
		
	}

}

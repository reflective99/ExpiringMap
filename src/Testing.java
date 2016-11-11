import java.math.BigInteger;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Testing {

	public static void main(String[] args) throws InterruptedException {
		Testing test = new Testing();
		System.out.println("Starting Test: ");
		test.testAddingValues();
	}
	
	public void testAddingValues() throws InterruptedException {
		ExpiringMap<String, String> map = new ExpiringMap<>(10);
		
		long startTime = System.currentTimeMillis();
		long seconds = 0;
		for(int i = 0; i < 100; i++) {
			String key = new BigInteger(10, new Random()).toString(32);
			String val = new BigInteger(20, new Random()).toString(32);
			map.put(key, val);
		}
				
		System.out.println(map);
	}

}

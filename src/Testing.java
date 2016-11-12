import java.math.BigInteger;
import java.util.Random;

public class Testing {

	public static void main(String[] args) throws InterruptedException {
		Testing test = new Testing();
		System.out.println("Starting Test: ");
		test.testAddingValues();
	}
	
	public void testAddingValues() throws InterruptedException {
		ExpiringMap<String, String> map = new ExpiringMap<>(10);
		
		for(int i = 0; i < 10000; i++) {
			String key = new BigInteger(10, new Random()).toString(32);
			String val = new BigInteger(20, new Random()).toString(32);
			map.put(key, val);
		}
				
		System.out.println(map);
		
		Thread.sleep(2000);
		
		System.out.println(map);
	}

}

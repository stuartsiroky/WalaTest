package exPkg;

public class MyTest {

	public void start(){
		ABase a = new ABase();
		Base b = new Base();
		
		a.m1();
		b.m2();
	}
	
	public static void main(String[] args) {
		MyTest t = new MyTest();
		t.start();
	}

}

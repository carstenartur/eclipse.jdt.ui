package p1;

public class A {

    B b;
    
    protected class InnerInterface {
        void innerMethod() {
            
        }
    }
}

class B {

	public void m(A a) {
	    A.InnerInterface inner = a.new InnerInterface();
	    inner.innerMethod();
	}
    
}

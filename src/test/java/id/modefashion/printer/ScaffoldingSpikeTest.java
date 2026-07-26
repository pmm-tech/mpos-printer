package id.modefashion.printer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

public class ScaffoldingSpikeTest {

  interface Greeter {
    String greet(String name);
  }

  @Test
  public void mockitoAndJunit4WorkTogether() {
    Greeter greeter = mock(Greeter.class);
    when(greeter.greet("world")).thenReturn("hello world");

    assertEquals("hello world", greeter.greet("world"));
    verify(greeter).greet("world");
  }
}

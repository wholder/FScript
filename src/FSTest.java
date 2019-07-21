//  This class provides basic abillity to run scripts in order to test
//  fscriptME.  Typically used to run regtest.script and any other
//  test scripts duting development

import murlen.util.fscriptME.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FSTest {
  static class BasicIO extends FScript {
    private Object[] files;

    BasicIO () {
      super();
      files = new Object[25];
    }

    public Object callFunction (String name, List<Object> param) throws FSException {
      switch (name) {
        case "println": {
          StringBuilder s = new StringBuilder();
          for (int n = 0; n < param.size(); n++) {
            s.append(param.get(n));
          }
          System.out.println(s);
          break;
        }
        case "readln":
          try {
            return new BufferedReader(new InputStreamReader(System.in)).readLine();
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
        case "open": {
          int n;
          try {
            for (n = 0; n < 25; n++) {
              if (files[n] == null) {
                if ((param.get(1)).equals("r")) {
                  files[n] = new BufferedReader(new FileReader((String) param.get(0)));
                  break;
                } else if ((param.get(1)).equals("w")) {
                  files[n] = new BufferedWriter(new FileWriter((String) param.get(0)));
                  break;
                } else {
                  throw new FSException("open expects 'r' or 'w' for modes");
                }
              }
            }
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
          if (n < 25) {
            return n;
          } else {
            return -1;
          }
        }
        case "close": {
          int n = (Integer) param.get(0);
          if (files[n] == null) {
            throw new FSException("Invalid file number passed to close");
          }
          try {
            if (files[n] instanceof BufferedWriter) {
              ((BufferedWriter) files[n]).close();
            } else {
              ((BufferedReader) files[n]).close();
            }
            files[n] = null;
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
          break;
        }
        case "write": {
          StringBuilder s = new StringBuilder();
          for (int ii = 1; ii < param.size(); ii++) {
            s.append(param.get(ii));
          }
          int n = (Integer) param.get(0);
          if (files[n] == null) {
            throw new FSException("Invalid file number passed to write");
          }
          if (!(files[n] instanceof BufferedWriter)) {
            throw new FSException("Invalid file mode for write");
          }
          try {
            ((BufferedWriter) files[n]).write(s.toString(), 0, s.length());
            ((BufferedWriter) files[n]).newLine();
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
          break;
        }
        case "read": {
          int n = (Integer) param.get(0);
          if (files[n] == null) {
            throw new FSException("Invalid file number passed to read");
          }
          if (!(files[n] instanceof BufferedReader)) {
            throw new FSException("Invalid file mode for read");
          }
          try {
            String s = ((BufferedReader) files[n]).readLine();
            if (s == null)
              s = "";
            return s;
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
        }
        case "eof": {
          int n;
          n = (Integer) param.get(0);
          if (files[n] == null) {
            throw new FSException("Invalid file number passed to eof");
          }
          BufferedReader br = (BufferedReader) files[n];
          try {
            br.mark(1024);
            if (br.readLine() == null) {
              return 1;
            } else {
              br.reset();
              return 0;
            }
          } catch (IOException e) {
            throw new FSException(e.getMessage());
          }
        }
        case "exit":
        case "abort":
          exit(param.get(0));
          break;
        default:
          super.callFunction(name, param);
          break;
      }
      return 0;
    }
  }

  private static String getFile (File file) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    byte[] data = new byte[fis.available()];
    //noinspection ResultOfMethodCallIgnored
    fis.read(data);
    fis.close();
    return new String(data, StandardCharsets.UTF_8);
  }


  public static void main (String[] args) throws FSException, IOException {
    BasicIO runner = new BasicIO();
    runner.addLines(getFile(new File("regtest.script")));
    Object ret = runner.runCode();
    System.out.println("Code returned: " + ret);
  }
}

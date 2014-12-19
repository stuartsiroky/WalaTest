package exWala;

import java.io.File;
import java.io.IOException;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

public class WalaTestEx {
	public static void main(String args[]) throws IOException, ClassHierarchyException {
		File exFile = new FileProvider()
				.getFile("C:\\Users\\StuartSiroky\\workspace_luna_wala\\exclustions.txt");
		System.out.println(exFile.getAbsolutePath());
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(
				"C:\\Users\\StuartSiroky\\workspace_luna_wala\\MyTest.jar",
				exFile);
		System.out.println("SCOPE: " + scope.toString());

		IClassHierarchy cha = ClassHierarchy.make(scope);
		for (IClass c : cha) {
			if (!scope.isApplicationLoader(c.getClassLoader()))
				continue;
			String cname = c.getName().toString();
			System.out.println("Class:" + cname);
			for (IMethod m : c.getAllMethods()) {
				String mname = m.getName().toString();
				System.out.println("  method:" + mname);
			} // for method
			System.out.println();

		} // for class

	} // main

} // endclass

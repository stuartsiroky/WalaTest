package exWala;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.slicer.SlicerTest;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.viz.NodeDecorator;

public class MyCallGraphGen {
	static int debugLevel = 6;
	/**
	 * Usage: MyCallGraphGen -scopeFile file_path [-entryClass class_name |
	 * -mainClass class_name]
	 * 
	 * If given -mainClass, uses main() method of class_name as entrypoint. If
	 * given -entryClass, uses all public methods of class_name.
	 * 
	 * @throws IOException
	 * @throws ClassHierarchyException
	 * @throws CallGraphBuilderCancelException
	 * @throws IllegalArgumentException
	 */

	public static void main(String[] args) throws IOException,
			ClassHierarchyException, IllegalArgumentException,
			CallGraphBuilderCancelException {
		long start = System.currentTimeMillis();
		// Properties p = CommandLine.parse(args);
		// String scopeFile = p.getProperty("scopeFile");
		// String entryClass = p.getProperty("entryClass");
		// String mainClass = p.getProperty("mainClass");
		String scopeFile = "C:\\Users\\StuartSiroky\\workspace_luna_wala\\WalaTest\\dat\\scopeList.txt";
		String entryClass = null;
		String mainClass = "LexPkg/MyTest";

		// if (mainClass != null && entryClass != null) {
		// throw new IllegalArgumentException(
		// "only specify one of mainClass or entryClass");
		// }
		// use exclusions to eliminate certain library packages
		String exFile = "C:\\Users\\StuartSiroky\\workspace_luna_wala\\WalaTest\\dat\\MyExclusions.txt";
		File exclusionsFile = new File(exFile);
		AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopeFile,
				exclusionsFile, MyCallGraphGen.class.getClassLoader());
		IClassHierarchy cha = ClassHierarchy.make(scope);
		if (debugLevel >= 1) {
			System.out.println(cha.getNumberOfClasses() + " classes");
		}
		if (debugLevel >= 6) {
			System.out.println(Warnings.asString());
		}
		Warnings.clear();

		printScopeClasses(scope, cha);

		AnalysisOptions options = new AnalysisOptions();
		Iterable<Entrypoint> entrypoints = entryClass != null ? makePublicEntrypoints(
				scope, cha, entryClass) : Util.makeMainEntrypoints(scope, cha,
				mainClass);
		options.setEntrypoints(entrypoints);
		// you can dial down reflection handling if you like
		// options.setReflectionOptions(ReflectionOptions.NONE);
		AnalysisCache cache = new AnalysisCache();
		// other builders can be constructed with different Util methods
		CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options,
				cache, cha, scope);
		// CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache,
		// cha, scope);
		// CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options,
		// cache, cha, scope);
		System.out.println("building call graph...");
		CallGraph cg = builder.makeCallGraph(options, null);
		long end = System.currentTimeMillis();
		System.out.println("done");
		System.out.println("took " + (end - start) + "ms");
		System.out.println(CallGraphStats.getStats(cg));
		// System.out.println(cg.toString());
		boolean goBackwards = true;
		String srcCaller = "start";// TODO 
		String srcCallee = "m2";// TODO

		DataDependenceOptions dOptions = Slicer.DataDependenceOptions.FULL;
		ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;

		try {
			makeSDG(cg, builder, srcCaller, srcCallee, goBackwards, dOptions,
					cOptions);
		} catch (CancelException e) {
			e.printStackTrace();
		}
	}

	private static void printScopeClasses(AnalysisScope scope,
			IClassHierarchy cha) {
		for (IClass c : cha) {
			if (!scope.isApplicationLoader(c.getClassLoader()))
				continue;
			String cname = c.getName().toString();
			if (debugLevel >= 2) {
				System.out.println("Class:" + cname);
				if (debugLevel >= 4) {
					for (IMethod m : c.getAllMethods()) {

						String mname = m.getName().toString();
						System.out.println("  method:" + mname + " "
								+ m.getDescriptor().toString());
					} // for method
				}
				System.out.println();
			}
		} // for class
	}

	private static Iterable<Entrypoint> makePublicEntrypoints(
			AnalysisScope scope, IClassHierarchy cha, String entryClass) {
		Collection<Entrypoint> result = new ArrayList<Entrypoint>();
		IClass klass = cha.lookupClass(TypeReference.findOrCreate(
				ClassLoaderReference.Application,
				StringStuff.deployment2CanonicalTypeString(entryClass)));
		for (IMethod m : klass.getDeclaredMethods()) {
			if (m.isPublic()) {
				result.add(new DefaultEntrypoint(m, cha));
			}
		}
		return result;
	}

	public static void makeSDG(CallGraph cg, CallGraphBuilder builder,
			String srcCaller, String srcCallee, boolean goBackward,
			DataDependenceOptions dOptions, ControlDependenceOptions cOptions)
			throws IllegalArgumentException, CancelException, IOException {
		SDG sdg = new SDG(cg, builder.getPointerAnalysis(), dOptions, cOptions);

		// find the call statement of interest
		CGNode callerNode = SlicerTest.findMethod(cg, srcCaller);
		Statement s = SlicerTest.findCallTo(callerNode, srcCallee);
		System.err.println("Statement: " + s);//WHY??
		// compute the slice as a collection of statements
		Collection<Statement> slice = null;
		if (goBackward) {
			slice = Slicer.computeBackwardSlice(s, cg,
					builder.getPointerAnalysis(), dOptions, cOptions);
		} else {
			// for forward slices ... we actually slice from the return
			// value of
			// calls.
			s = getReturnStatementForCall(s);
			slice = Slicer.computeForwardSlice(s, cg,
					builder.getPointerAnalysis(), dOptions, cOptions);
		}
		SlicerTest.dumpSlice(slice);

		// create a view of the SDG restricted to nodes in the slice
		Graph<Statement> g = pruneSDG(sdg, slice);

		sanityCheck(slice, g);

	} // makeSDG


	/**
	 * If s is a call statement, return the statement representing the normal
	 * return from s
	 */
	public static Statement getReturnStatementForCall(Statement s) {
		if (s.getKind() == Kind.NORMAL) {
			NormalStatement n = (NormalStatement) s;
			SSAInstruction st = n.getInstruction();
			if (st instanceof SSAInvokeInstruction) {
				SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) st;
				if (call.getCallSite().getDeclaredTarget().getReturnType()
						.equals(TypeReference.Void)) {
					throw new IllegalArgumentException(
							"this driver computes forward slices from the return value of calls.\n"
									+ ""
									+ "Method "
									+ call.getCallSite().getDeclaredTarget()
											.getSignature() + " returns void.");
				}
				return new NormalReturnCaller(s.getNode(),
						n.getInstructionIndex());
			} else {
				return s;
			}
		} else {
			return s;
		}
	}

	/**
	 * check that g is a well-formed graph, and that it contains exactly the
	 * number of nodes in the slice
	 */
	private static void sanityCheck(Collection<Statement> slice,
			Graph<Statement> g) {
		try {
			GraphIntegrity.check(g);
		} catch (UnsoundGraphException e1) {
			e1.printStackTrace();
			Assertions.UNREACHABLE();
		}
		Assertions.productionAssertion(g.getNumberOfNodes() == slice.size(),
				"panic " + g.getNumberOfNodes() + " " + slice.size());
	}

	public static Graph<Statement> pruneSDG(SDG sdg,
			final Collection<Statement> slice) {
		Predicate<Statement> f = new Predicate<Statement>() {
			@Override
			public boolean test(Statement o) {
				return slice.contains(o);
			}
		};
		return GraphSlicer.prune(sdg, f);
	}

	/**
	 * @return a NodeDecorator that decorates statements in a slice for a
	 *         dot-ted representation
	 */
	public static NodeDecorator<Statement> makeNodeDecorator() {
		return new NodeDecorator<Statement>() {
			@Override
			public String getLabel(Statement s) throws WalaException {
				switch (s.getKind()) {
				case HEAP_PARAM_CALLEE:
				case HEAP_PARAM_CALLER:
				case HEAP_RET_CALLEE:
				case HEAP_RET_CALLER:
					HeapStatement h = (HeapStatement) s;
					return s.getKind() + "\\n" + h.getNode() + "\\n"
							+ h.getLocation();
				case NORMAL:
					NormalStatement n = (NormalStatement) s;
					return n.getInstruction() + "\\n"
							+ n.getNode().getMethod().getSignature();
				case PARAM_CALLEE:
					ParamCallee paramCallee = (ParamCallee) s;
					return s.getKind() + " " + paramCallee.getValueNumber()
							+ "\\n" + s.getNode().getMethod().getName();
				case PARAM_CALLER:
					ParamCaller paramCaller = (ParamCaller) s;
					return s.getKind()
							+ " "
							+ paramCaller.getValueNumber()
							+ "\\n"
							+ s.getNode().getMethod().getName()
							+ "\\n"
							+ paramCaller.getInstruction().getCallSite()
									.getDeclaredTarget().getName();
				case EXC_RET_CALLEE:
				case EXC_RET_CALLER:
				case NORMAL_RET_CALLEE:
				case NORMAL_RET_CALLER:
				case PHI:
				default:
					return s.toString();
				}
			}

		};
	}

} // end MyCallGraphGen

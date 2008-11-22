package gr.uom.java.ast.decomposition.cfg;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldInstructionObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.TypeObject;
import gr.uom.java.ast.decomposition.AbstractStatement;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class PDGNode extends GraphNode implements Comparable<PDGNode> {
	private CFGNode cfgNode;
	protected Set<Variable> declaredVariables;
	protected Set<Variable> definedVariables;
	protected Set<Variable> usedVariables;
	private Map<VariableDeclaration, LinkedHashSet<MethodInvocation>> stateChangingMethodInvocationMap;
	private Set<MethodInvocation> processedMethodInvocations;
	
	public PDGNode() {
		super();
		this.declaredVariables = new LinkedHashSet<Variable>();
		this.definedVariables = new LinkedHashSet<Variable>();
		this.usedVariables = new LinkedHashSet<Variable>();
		this.stateChangingMethodInvocationMap = new LinkedHashMap<VariableDeclaration, LinkedHashSet<MethodInvocation>>();
		this.processedMethodInvocations = new LinkedHashSet<MethodInvocation>();
	}
	
	public PDGNode(CFGNode cfgNode) {
		super();
		this.cfgNode = cfgNode;
		this.id = cfgNode.id;
		cfgNode.setPDGNode(this);
		this.declaredVariables = new LinkedHashSet<Variable>();
		this.definedVariables = new LinkedHashSet<Variable>();
		this.usedVariables = new LinkedHashSet<Variable>();
		this.stateChangingMethodInvocationMap = new LinkedHashMap<VariableDeclaration, LinkedHashSet<MethodInvocation>>();
		this.processedMethodInvocations = new LinkedHashSet<MethodInvocation>();
	}

	public CFGNode getCFGNode() {
		return cfgNode;
	}

	public Iterator<GraphEdge> getOutgoingDependenceIterator() {
		return outgoingEdges.iterator();
	}

	public Iterator<GraphEdge> getIncomingDependenceIterator() {
		return incomingEdges.iterator();
	}

	public boolean declaresLocalVariable(Variable variable) {
		return declaredVariables.contains(variable);
	}

	public boolean definesLocalVariable(Variable variable) {
		return definedVariables.contains(variable);
	}

	public boolean usesLocalVariable(Variable variable) {
		return usedVariables.contains(variable);
	}

	public Set<VariableDeclaration> getStateChangingVariables() {
		Set<VariableDeclaration> stateChangingVariables = new LinkedHashSet<VariableDeclaration>();
		stateChangingVariables.addAll(stateChangingMethodInvocationMap.keySet());
		for(Variable variable : definedVariables) {
			VariableDeclaration reference = variable.getReference();
			if(reference != null)
				stateChangingVariables.add(reference);
		}
		return stateChangingVariables;
	}

	public BasicBlock getBasicBlock() {
		return cfgNode.getBasicBlock();
	}

	public AbstractStatement getStatement() {
		return cfgNode.getStatement();
	}

	public Statement getASTStatement() {
		return cfgNode.getASTStatement();
	}

	public boolean equals(Object o) {
		if(this == o)
    		return true;
    	
    	if(o instanceof PDGNode) {
    		PDGNode pdgNode = (PDGNode)o;
    		return this.cfgNode.equals(pdgNode.cfgNode);
    	}
    	return false;
	}

	public int hashCode() {
		return cfgNode.hashCode();
	}

	public String toString() {
		return cfgNode.toString();
	}

	public int compareTo(PDGNode node) {
		if(this.getId() > node.getId())
			return 1;
		else if(this.getId() < node.getId())
			return -1;
		else
			return 0;
	}

	public String getAnnotation() {
		return "Def = " + definedVariables + " , Use = " + usedVariables;
	}

	private void putInStateChangingMethodInvocationMap(VariableDeclaration variableDeclaration, MethodInvocation methodInvocation) {
		if(stateChangingMethodInvocationMap.containsKey(variableDeclaration)) {
			LinkedHashSet<MethodInvocation> methodInvocations = stateChangingMethodInvocationMap.get(variableDeclaration);
			methodInvocations.add(methodInvocation);
		}
		else {
			LinkedHashSet<MethodInvocation> methodInvocations = new LinkedHashSet<MethodInvocation>();
			methodInvocations.add(methodInvocation);
			stateChangingMethodInvocationMap.put(variableDeclaration, methodInvocations);
		}
	}

	protected void processExternalMethodInvocation(MethodInvocation methodInvocation, VariableDeclaration variableDeclaration) {
		IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
		ITypeBinding declaringClassBinding = methodBinding.getDeclaringClass();
		TypeObject type = TypeObject.extractTypeObject(declaringClassBinding.getQualifiedName());
		String classType = type.getClassType();
		String methodInvocationName = methodInvocation.getName().getIdentifier();
		Variable variable = new Variable(variableDeclaration);
		if(classType.equals("java.util.Iterator") && methodInvocationName.equals("next")) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
		else if(classType.equals("java.util.Enumeration") && methodInvocationName.equals("nextElement")) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
		else if(classType.equals("java.util.ListIterator") &&
				(methodInvocationName.equals("next") || methodInvocationName.equals("previous"))) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
		else if((classType.equals("java.util.Collection") || classType.equals("java.util.AbstractCollection") ||
				classType.equals("java.util.List") || classType.equals("java.util.AbstractList") ||
				classType.equals("java.util.ArrayList") || classType.equals("java.util.LinkedList") ||
				classType.equals("java.util.Set") || classType.equals("java.util.AbstractSet") ||
				classType.equals("java.util.HashSet") || classType.equals("java.util.LinkedHashSet") ||
				classType.equals("java.util.SortedSet") || classType.equals("java.util.TreeSet") ||
				classType.equals("java.util.Vector") || classType.equals("java.util.Stack")) &&
				(methodInvocationName.equals("add") || methodInvocationName.equals("remove") ||
						methodInvocationName.equals("addAll") || methodInvocationName.equals("removeAll") ||
						methodInvocationName.equals("addFirst") || methodInvocationName.equals("removeFirst") ||
						methodInvocationName.equals("addLast") || methodInvocationName.equals("removeLast") ||
						methodInvocationName.equals("addElement") || methodInvocationName.equals("removeElement") ||
						methodInvocationName.equals("insertElementAt") || methodInvocationName.equals("removeElementAt") ||
						methodInvocationName.equals("retainAll") || methodInvocationName.equals("set") ||
						methodInvocationName.equals("setElementAt") || methodInvocationName.equals("clear") ||
						methodInvocationName.equals("removeAllElements") ||
						methodInvocationName.equals("push") || methodInvocationName.equals("pop"))) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
		else if((classType.equals("java.util.Map") || classType.equals("java.util.AbstractMap") ||
				classType.equals("java.util.HashMap") || classType.equals("java.util.Hashtable") ||
				classType.equals("java.util.IdentityHashMap") || classType.equals("java.util.WeakHashMap") ||
				classType.equals("java.util.SortedMap") || classType.equals("java.util.TreeMap")) &&
				(methodInvocationName.equals("put") || methodInvocationName.equals("remove") ||
						methodInvocationName.equals("putAll") || methodInvocationName.equals("clear"))) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
		else if((classType.equals("java.util.Queue") || classType.equals("java.util.AbstractQueue") || classType.equals("java.util.Deque") ||
				classType.equals("java.util.concurrent.BlockingQueue") || classType.equals("java.util.concurrent.BlockingDeque") ||
				classType.equals("java.util.concurrent.ArrayBlockingQueue") || classType.equals("java.util.ArrayDeque") ||
				classType.equals("java.util.concurrent.ConcurrentLinkedQueue") || classType.equals("java.util.concurrent.DelayQueue") ||
				classType.equals("java.util.concurrent.LinkedBlockingDeque") || classType.equals("java.util.concurrent.LinkedBlockingQueue") ||
				classType.equals("java.util.concurrent.PriorityBlockingQueue") || classType.equals("java.util.PriorityQueue") ||
				classType.equals("java.util.concurrent.SynchronousQueue")) &&
				(methodInvocationName.equals("add") || methodInvocationName.equals("offer") ||
						methodInvocationName.equals("remove") || methodInvocationName.equals("poll") ||
						methodInvocationName.equals("addAll") || methodInvocationName.equals("clear") ||
						methodInvocationName.equals("drainTo") ||
						methodInvocationName.equals("addFirst") || methodInvocationName.equals("addLast") ||
						methodInvocationName.equals("offerFirst") || methodInvocationName.equals("offerLast") ||
						methodInvocationName.equals("removeFirst") || methodInvocationName.equals("removeLast") ||
						methodInvocationName.equals("pollFirst") || methodInvocationName.equals("pollLast") ||
						methodInvocationName.equals("removeFirstOccurrence") || methodInvocationName.equals("removeLastOccurrence") ||
						methodInvocationName.equals("put") || methodInvocationName.equals("take") ||
						methodInvocationName.equals("putFirst") || methodInvocationName.equals("putLast") ||
						methodInvocationName.equals("takeFirst") || methodInvocationName.equals("takeLast"))) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			definedVariables.add(variable);
		}
	}

	protected void processInternalMethodInvocation(ClassObject classObject, MethodObject methodObject,
			MethodInvocation methodInvocation, VariableDeclaration variableDeclaration) {
		List<FieldInstructionObject> fieldInstructions = methodObject.getFieldInstructions();
		boolean stateChangingMethodInvocation = false;
		for(FieldInstructionObject fieldInstruction : fieldInstructions) {
			VariableDeclaration fieldDeclaration = null;
			ListIterator<FieldObject> fieldIterator = classObject.getFieldIterator();
			while(fieldIterator.hasNext()) {
				FieldObject fieldObject = fieldIterator.next();
				VariableDeclarationFragment fragment = fieldObject.getVariableDeclarationFragment();
				if(fragment.resolveBinding().isEqualTo(fieldInstruction.getSimpleName().resolveBinding())) {
					fieldDeclaration = fragment;
					break;
				}
			}
			if(fieldDeclaration != null) {
				Variable field = null;
				if(variableDeclaration != null)
					field = new Variable(variableDeclaration, fieldDeclaration);
				else
					field = new Variable(fieldDeclaration);
				List<Assignment> fieldAssignments = methodObject.getFieldAssignments(fieldInstruction);
				List<PostfixExpression> fieldPostfixAssignments = methodObject.getFieldPostfixAssignments(fieldInstruction);
				List<PrefixExpression> fieldPrefixAssignments = methodObject.getFieldPrefixAssignments(fieldInstruction);
				if(!fieldAssignments.isEmpty()) {
					definedVariables.add(field);
					for(Assignment assignment : fieldAssignments) {
						Assignment.Operator operator = assignment.getOperator();
						if(!operator.equals(Assignment.Operator.ASSIGN))
							usedVariables.add(field);
					}
					stateChangingMethodInvocation = true;
				}
				else if(!fieldPostfixAssignments.isEmpty()) {
					definedVariables.add(field);
					usedVariables.add(field);
					stateChangingMethodInvocation = true;
				}
				else if(!fieldPrefixAssignments.isEmpty()) {
					definedVariables.add(field);
					usedVariables.add(field);
					stateChangingMethodInvocation = true;
				}
				else {
					usedVariables.add(field);
				}
			}
		}
		if(stateChangingMethodInvocation && variableDeclaration != null) {
			putInStateChangingMethodInvocationMap(variableDeclaration, methodInvocation);
			Variable variable = new Variable(variableDeclaration);
			definedVariables.add(variable);
		}
		processedMethodInvocations.add(methodInvocation);
		List<MethodInvocationObject> methodInvocations = methodObject.getMethodInvocations();
		for(MethodInvocationObject methodInvocationObject : methodInvocations) {
			MethodInvocation methodInvocation2 = methodInvocationObject.getMethodInvocation();
			if(methodInvocation2.getExpression() == null || methodInvocation2.getExpression() instanceof ThisExpression) {
				MethodObject methodObject2 = classObject.getMethod(methodInvocationObject);
				if(methodObject2 != null && !methodObject2.equals(methodObject)) {
					if(!processedMethodInvocations.contains(methodInvocation2))
						processInternalMethodInvocation(classObject, methodObject2, methodInvocation2, variableDeclaration);
				}
			}
		}
	}

	protected VariableDeclaration processDirectFieldModification(SimpleName fieldInstructionName, Set<VariableDeclaration> variableDeclarationsInMethod) {
		SimpleName qualifier = null;
		if(fieldInstructionName.getParent() instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess)fieldInstructionName.getParent();
			if(fieldAccess.getExpression() instanceof FieldAccess) {
				FieldAccess fieldAccessExpression = (FieldAccess)fieldAccess.getExpression();
				if(fieldAccessExpression.getExpression() instanceof ThisExpression)
					qualifier = fieldAccessExpression.getName();
			}
		}
		else if(fieldInstructionName.getParent() instanceof QualifiedName) {
			QualifiedName qualifiedName = (QualifiedName)fieldInstructionName.getParent();
			if(qualifiedName.getQualifier() instanceof SimpleName) {
				qualifier = (SimpleName)qualifiedName.getQualifier();
			}
		}
		if(qualifier != null) {
			IBinding binding = qualifier.resolveBinding();
			if(binding.getKind() == IBinding.VARIABLE) {
				IVariableBinding variableBinding = (IVariableBinding)binding;
				VariableDeclaration variableDeclaration = null;
				if(variableBinding.isField()) {
					ITypeBinding declaringClassBinding = variableBinding.getDeclaringClass();
					SystemObject systemObject = ASTReader.getSystemObject();
					ClassObject classObject = systemObject.getClassObject(declaringClassBinding.getQualifiedName());
					if(classObject != null) {
						ListIterator<FieldObject> fieldIterator = classObject.getFieldIterator();
						while(fieldIterator.hasNext()) {
							FieldObject fieldObject = fieldIterator.next();
							VariableDeclarationFragment fragment = fieldObject.getVariableDeclarationFragment();
							if(fragment.resolveBinding().isEqualTo(qualifier.resolveBinding())) {
								variableDeclaration = fragment;
								break;
							}
						}
					}
				}
				else {
					for(VariableDeclaration declaration : variableDeclarationsInMethod) {
						if(declaration.resolveBinding().isEqualTo(qualifier.resolveBinding())) {
							variableDeclaration = declaration;
							break;
						}
					}
				}
				return variableDeclaration;
			}
		}
		return null;
	}
}

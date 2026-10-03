package org.falpi.utils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// #############################################################################
// Classe principale
// #############################################################################
public class DumpUtils {

   private DumpUtils() {}

   // MAX_DEPTH: stops recursion when nesting of non-ignored objects exceeds this.
   // Produces max-depth="true" on the truncated object element.
   private static final int MAX_DEPTH = 100;
   private static final ThreadLocal<Integer> CURRENT_DEPTH = new ThreadLocal<Integer>() {
      protected Integer initialValue() { return 0; }
   };

   // #############################################################################
   // Esegue dump in formato XML (streaming StAX - zero overhead DOM)
   // #############################################################################
   //
   //  Usage:
   //    String xml = DumpUtils.dumpObjectToXml(myObject, true);   // with parameters
   //    String xml = DumpUtils.dumpObjectToXml(myObject, false);  // without parameters
   //
   //  Output includes:
   //   - type-hierarchy  : superclass chain + all implemented interfaces
   //   - static-fields   : static fields grouped by declaring class
   //   - instance-fields : instance fields grouped by declaring class (superclass first)
   //   - methods         : signatures only, grouped by declaring class/interface
   //                       (java.lang.Object and ignored classes excluded)
   //
   //  Design choices:
   //   - Attribute order guaranteed by StAX insertion order (no alphabetic sorting).
   //   - Field attributes: name, declaredType, runtimeType, value/null/circular-ref,
   //                       modifiers, synthetic, genericType.
   //   - Object attributes: declaredType, runtimeType, id.
   //   - runtimeType only when different from declaredType.
   //   - Primitives: boxing artifact suppressed (no runtimeType for primitive fields).
   //   - Leaf types rendered as value="..." (primitives, wrappers, String, Enum,
   //     java.time.*, java.math.*, Date, UUID).
   //   - Ignored classes rendered as value=toString() without recursion.
   //   - Circular references: circular-ref="@id" attribute, no recursion.
   //   - Synthetic fields included (val$xxx, this$0 in anonymous/inner classes).
   //   - MAX_DEPTH=10 caps recursion depth to prevent stack overflow.
   //   - Self-closing elements (<field/>, <method/>) used whenever possible.


   // #############################################################################
   // Esegue dump in formato XML (streaming StAX)
   // #############################################################################

   /**
    * Variabili di lavoro passate esplicitamente lungo tutta la catena.
    *  visited      : oggetti gi? visitati (Map + Collection + Array inclusi) -> anti-loop
    *  dumpedClasses: classi gi? dumpate integralmente (type-hierarchy + static-fields + methods)
    *                 Per le istanze successive viene scritto solo instance-fields.
    */
   private static final class DumpContext {
      final Map<Object, String> visited        = new IdentityHashMap<Object, String>();
      final Set<String>         dumpedClasses  = new HashSet<String>(); // className già dumpati
      final String[]            ignoredPrefixes;

      DumpContext(String[] prefixes) {
         this.ignoredPrefixes = (prefixes != null) ? prefixes : new String[0];
      }
   }

   public static void dumpObjectToXml(Object root, boolean ip, java.io.OutputStream out, String... ignoredPrefixes) throws Exception {
      XMLStreamWriter w   = XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8");
      DumpContext     ctx = new DumpContext(ignoredPrefixes);

      w.writeStartDocument("UTF-8", "1.0");
      if (root == null) {
         w.writeCharacters("\n");
         w.writeEmptyElement("object");
         w.writeAttribute("null", "true");
      } else {
         buildObjectElement(w, ctx, root, root.getClass(), ip, 0);
      }
      w.writeCharacters("\n");
      w.writeEndDocument();
      w.flush();
   }

   // =============================================================================
   // Metodi a supporto
   // =============================================================================

   // -----------------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------------

   private static void writeIndent(XMLStreamWriter w, int indent) throws Exception {
      w.writeCharacters("\n");
      for (int i = 0; i < indent; i++) w.writeCharacters("  ");
   }

   /**
    * True quando il valore richiede un elemento figlio XML.
    * Collection, Map e array sono SEMPRE espansi come figli anche se la loro
    * classe rientra in isIgnoredClass (es. java.util.ArrayList).
    * Se l'istanza ? gi? in visited viene scritta come circular-ref (attributo).
    */
   private static boolean needsChildElement(Object value, DumpContext ctx) {
      if (value == null || isLeaf(value)) return false;
      if (ctx.visited.containsKey(value))     return false; // circular-ref -> attributo
      if (value instanceof Collection<?> || value instanceof Map<?,?> || value.getClass().isArray())
         return true;  // sempre espansi anche se classe ignorata
      if (isIgnoredClass(value.getClass(), ctx)) return false; // toString -> attributo
      return true;
   }

   private static String safeToString(Object obj) {
      try   { String s = String.valueOf(obj); return s != null ? s : ""; }
      catch (Exception e) { return "<toString-error:" + e.getClass().getSimpleName() + ">"; }
   }

   private static String register(DumpContext ctx, Object obj) {
      String id = "@" + Integer.toHexString(System.identityHashCode(obj));
      ctx.visited.put(obj, id);
      return id;
   }

   // -----------------------------------------------------------------------------
   // Root element
   // -----------------------------------------------------------------------------

   private static void buildObjectElement(XMLStreamWriter w, DumpContext ctx,
         Object obj, Type declaredType, boolean ip, int indent) throws Exception {
      Class<?> runtimeClass = obj.getClass();
      String   declared     = rawName(declaredType);
      String   runtime      = runtimeClass.getName();
      String   id           = register(ctx, obj);

      int     depth     = CURRENT_DEPTH.get();
      boolean truncated = depth >= MAX_DEPTH;

      writeIndent(w, indent);
      if (truncated) {
         w.writeEmptyElement("object");
         w.writeAttribute("declaredType", declared);
         if (!runtime.equals(declared)) w.writeAttribute("runtimeType", runtime);
         w.writeAttribute("id", id);
         w.writeAttribute("max-depth", "true");
         return;
      }

      w.writeStartElement("object");
      w.writeAttribute("declaredType", declared);
      if (!runtime.equals(declared)) w.writeAttribute("runtimeType", runtime);
      w.writeAttribute("id", id);

      CURRENT_DEPTH.set(depth + 1);
      try {
         if (!ctx.dumpedClasses.contains(runtimeClass.getName())) {
            // Prima istanza della classe: dump completo.
            // Registra tutte le classi non-ignorate della gerarchia cos? le
            // istanze successive di qualsiasi classe in essa vengono ottimizzate.
            for (Class<?> c : hierarchy(runtimeClass, true))
               if (!isIgnoredClass(c, ctx)) ctx.dumpedClasses.add(c.getName());
            buildTypeHierarchy(w, runtimeClass, indent + 1);
            buildMethods(w, ctx, runtimeClass, ip, indent + 1);
            buildStaticFields(w, ctx, runtimeClass, ip, indent + 1);
            buildInstanceFields(w, ctx, obj, runtimeClass, ip, indent + 1);
         } else {
            // Classe gi? dumpata: solo instance-fields, nient'altro.
            buildInstanceFields(w, ctx, obj, runtimeClass, ip, indent + 1);
         }
      } finally {
         CURRENT_DEPTH.set(depth);
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   // -----------------------------------------------------------------------------
   // Type hierarchy
   // -----------------------------------------------------------------------------

   private static void buildTypeHierarchy(XMLStreamWriter w, Class<?> clazz,
         int indent) throws Exception {
      writeIndent(w, indent);
      w.writeStartElement("type-hierarchy");

      writeIndent(w, indent + 1);
      w.writeStartElement("class");
      w.writeAttribute("name",          clazz.getSimpleName());
      w.writeAttribute("qualifiedName", clazz.getName());
      w.writeAttribute("modifiers",     Modifier.toString(clazz.getModifiers()));

      List<Class<?>> supers = new ArrayList<Class<?>>();
      for (Class<?> c = clazz.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass())
         supers.add(c);

      writeIndent(w, indent + 2);
      if (supers.isEmpty()) {
         w.writeEmptyElement("superclass-chain");
      } else {
         w.writeStartElement("superclass-chain");
         for (Class<?> c : supers) {
            writeIndent(w, indent + 3);
            w.writeEmptyElement("superclass");
            w.writeAttribute("qualifiedName", c.getName());
         }
         writeIndent(w, indent + 2);
         w.writeEndElement();
      }

      LinkedHashSet<Class<?>> ifaces = collectInterfaces(clazz);
      writeIndent(w, indent + 2);
      if (ifaces.isEmpty()) {
         w.writeEmptyElement("interfaces");
      } else {
         w.writeStartElement("interfaces");
         for (Class<?> iface : ifaces) {
            writeIndent(w, indent + 3);
            w.writeEmptyElement("interface");
            w.writeAttribute("qualifiedName", iface.getName());
         }
         writeIndent(w, indent + 2);
         w.writeEndElement();
      }

      writeIndent(w, indent + 1);
      w.writeEndElement(); // </class>
      writeIndent(w, indent);
      w.writeEndElement(); // </type-hierarchy>
   }

   private static LinkedHashSet<Class<?>> collectInterfaces(Class<?> clazz) {
      LinkedHashSet<Class<?>> result = new LinkedHashSet<Class<?>>();
      for (Class<?> c = clazz; c != null; c = c.getSuperclass())
         for (Class<?> iface : c.getInterfaces())
            addInterface(iface, result);
      return result;
   }

   private static void addInterface(Class<?> iface, Set<Class<?>> result) {
      if (result.add(iface))
         for (Class<?> parent : iface.getInterfaces())
            addInterface(parent, result);
   }

   // -----------------------------------------------------------------------------
   // Fields
   // -----------------------------------------------------------------------------

   private static void buildStaticFields(XMLStreamWriter w, DumpContext ctx,
         Class<?> clazz, boolean ip, int indent) throws Exception {
      boolean hasContent = false;
      for (Class<?> c : hierarchy(clazz, true)) {
         if (!isIgnoredClass(c, ctx) && !declaredFields(c, true).isEmpty()) { hasContent = true; break; }
      }
      writeIndent(w, indent);
      if (!hasContent) { w.writeEmptyElement("static-fields"); return; }
      w.writeStartElement("static-fields");
      for (Class<?> c : hierarchy(clazz, true)) {
         if (isIgnoredClass(c, ctx)) continue;
         List<Field> fields = declaredFields(c, true);
         if (!fields.isEmpty()) buildFieldGroup(w, ctx, null, c, fields, ip, indent + 1);
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildInstanceFields(XMLStreamWriter w, DumpContext ctx,
         Object obj, Class<?> clazz, boolean ip, int indent) throws Exception {
      boolean hasContent = false;
      for (Class<?> c : hierarchy(clazz, true)) {
         if (!isIgnoredClass(c, ctx) && !declaredFields(c, false).isEmpty()) { hasContent = true; break; }
      }
      writeIndent(w, indent);
      if (!hasContent) { w.writeEmptyElement("instance-fields"); return; }
      w.writeStartElement("instance-fields");
      for (Class<?> c : hierarchy(clazz, true)) {
         if (isIgnoredClass(c, ctx)) continue;
         List<Field> fields = declaredFields(c, false);
         if (!fields.isEmpty()) buildFieldGroup(w, ctx, obj, c, fields, ip, indent + 1);
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildFieldGroup(XMLStreamWriter w, DumpContext ctx,
         Object holder, Class<?> declaring, List<Field> fields,
         boolean ip, int indent) throws Exception {
      writeIndent(w, indent);
      w.writeStartElement("field-group");
      w.writeAttribute("declaringClass", declaring.getName());
      for (Field f : fields) {
         try {
            f.setAccessible(true);
            buildField(w, ctx, f, f.get(holder), ip, indent + 1);
         } catch (Exception e) {
            writeIndent(w, indent + 1);
            w.writeEmptyElement("field");
            w.writeAttribute("name",         f.getName());
            w.writeAttribute("access-error", e.getClass().getSimpleName()
                  + (e.getMessage() != null ? ": " + e.getMessage() : ""));
         }
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildField(XMLStreamWriter w, DumpContext ctx,
         Field f, Object value, boolean ip, int indent) throws Exception {
      String  declaredTypeName = rawName(f.getGenericType());
      boolean hasChild         = needsChildElement(value, ctx);

      writeIndent(w, indent);
      if (hasChild) w.writeStartElement("field");
      else          w.writeEmptyElement("field");

      // Ordine attributi: name, declaredType, runtimeType, value/null/circular-ref, modifiers, synthetic, genericType
      w.writeAttribute("name",         f.getName());
      w.writeAttribute("declaredType", declaredTypeName);

      if (value != null && !f.getType().isPrimitive()) {
         String runtime = value.getClass().getName();
         if (!runtime.equals(declaredTypeName)) w.writeAttribute("runtimeType", runtime);
      }

      if (value == null) {
         w.writeAttribute("null", "true");
      } else if (!isLeaf(value) && ctx.visited.containsKey(value)) {
         w.writeAttribute("circular-ref", ctx.visited.get(value));
      } else if (!hasChild) {
         if      (isLeaf(value))                    w.writeAttribute("value", leafString(value));
         else if (isIgnoredClass(value.getClass(), ctx)) w.writeAttribute("value", safeToString(value));
      }

      w.writeAttribute("modifiers", Modifier.toString(f.getModifiers()));
      if (f.isSynthetic()) w.writeAttribute("synthetic", "true");
      if (f.getGenericType() instanceof ParameterizedType)
         w.writeAttribute("genericType", typeName(f.getGenericType()));

      if (hasChild) {
         writeFieldValue(w, ctx, value, f.getGenericType(), ip, indent + 1);
         writeIndent(w, indent);
         w.writeEndElement();
      }
   }

   // -----------------------------------------------------------------------------
   // Value dispatch
   // -----------------------------------------------------------------------------

   private static void writeFieldValue(XMLStreamWriter w, DumpContext ctx,
         Object value, Type declaredType, boolean ip, int indent) throws Exception {
      Class<?> cls = value.getClass();
      if      (cls.isArray())              buildArray(w, ctx, value, ip, indent);
      else if (value instanceof Collection<?>) buildCollection(w, ctx, (Collection<?>) value, ip, indent);
      else if (value instanceof Map<?,?>)      buildMap(w, ctx, (Map<?,?>) value, ip, indent);
      else                                     buildObjectElement(w, ctx, value, declaredType, ip, indent);
   }

   /**
    * Ottimizzazione A: ogni Array viene registrato in visited alla prima espansione.
    * Se incontrato di nuovo -> circular-ref sul campo padre (gestito da needsChildElement).
    */
   private static void buildArray(XMLStreamWriter w, DumpContext ctx,
         Object array, boolean ip, int indent) throws Exception {
      String id  = register(ctx, array);
      int    len = Array.getLength(array);
      writeIndent(w, indent);
      if (len == 0) {
         w.writeEmptyElement("array");
         w.writeAttribute("id",            id);
         w.writeAttribute("size",          "0");
         w.writeAttribute("componentType", array.getClass().getComponentType().getName());
         return;
      }
      w.writeStartElement("array");
      w.writeAttribute("id",            id);
      w.writeAttribute("size",          String.valueOf(len));
      w.writeAttribute("componentType", array.getClass().getComponentType().getName());
      for (int i = 0; i < len; i++) {
         Object item = Array.get(array, i);
         buildElement(w, ctx, i, item, item != null ? item.getClass() : null, ip, indent + 1);
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   /**
    * Ottimizzazione A: ogni Collection viene registrata in visited alla prima espansione.
    */
   private static void buildCollection(XMLStreamWriter w, DumpContext ctx,
         Collection<?> col, boolean ip, int indent) throws Exception {
      String id = register(ctx, col);
      writeIndent(w, indent);
      if (col.isEmpty()) {
         w.writeEmptyElement("collection");
         w.writeAttribute("id",   id);
         w.writeAttribute("size", "0");
         return;
      }
      w.writeStartElement("collection");
      w.writeAttribute("id",   id);
      w.writeAttribute("size", String.valueOf(col.size()));
      int i = 0;
      for (Object item : col)
         buildElement(w, ctx, i++, item, item != null ? item.getClass() : null, ip, indent + 1);
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildElement(XMLStreamWriter w, DumpContext ctx,
         int index, Object item, Type type, boolean ip, int indent) throws Exception {
      boolean hasChild = needsChildElement(item, ctx);
      writeIndent(w, indent);
      if (hasChild) w.writeStartElement("element");
      else          w.writeEmptyElement("element");

      w.writeAttribute("index", String.valueOf(index));
      if (item == null) {
         w.writeAttribute("null", "true");
      } else if (!isLeaf(item) && ctx.visited.containsKey(item)) {
         w.writeAttribute("circular-ref", ctx.visited.get(item));
      } else {
         w.writeAttribute("declaredType", rawName(type));
         if      (isLeaf(item))                    w.writeAttribute("value", leafString(item));
         else if (isIgnoredClass(item.getClass(), ctx)) w.writeAttribute("value", safeToString(item));
      }
      if (hasChild) {
         writeFieldValue(w, ctx, item, type, ip, indent + 1);
         writeIndent(w, indent);
         w.writeEndElement();
      }
   }

   /**
    * Ottimizzazione A: ogni Map viene registrata in visited alla prima espansione.
    */
   private static void buildMap(XMLStreamWriter w, DumpContext ctx,
         Map<?,?> map, boolean ip, int indent) throws Exception {
      String id = register(ctx, map);
      writeIndent(w, indent);
      if (map.isEmpty()) {
         w.writeEmptyElement("map");
         w.writeAttribute("id",   id);
         w.writeAttribute("size", "0");
         return;
      }
      w.writeStartElement("map");
      w.writeAttribute("id",   id);
      w.writeAttribute("size", String.valueOf(map.size()));
      for (Map.Entry<?,?> entry : map.entrySet()) {
         writeIndent(w, indent + 1);
         w.writeStartElement("entry");
         buildKeyValue(w, ctx, "key",   entry.getKey(),   ip, indent + 2);
         buildKeyValue(w, ctx, "value", entry.getValue(), ip, indent + 2);
         writeIndent(w, indent + 1);
         w.writeEndElement();
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildKeyValue(XMLStreamWriter w, DumpContext ctx,
         String tag, Object val, boolean ip, int indent) throws Exception {
      boolean hasChild = needsChildElement(val, ctx);
      writeIndent(w, indent);
      if (hasChild) w.writeStartElement(tag);
      else          w.writeEmptyElement(tag);

      if (val == null) {
         w.writeAttribute("null", "true");
      } else {
         w.writeAttribute("declaredType", val.getClass().getName());
         if      (!isLeaf(val) && ctx.visited.containsKey(val)) w.writeAttribute("circular-ref", ctx.visited.get(val));
         else if (isLeaf(val))                                   w.writeAttribute("value", leafString(val));
         else if (isIgnoredClass(val.getClass(), ctx))                w.writeAttribute("value", safeToString(val));
      }
      if (hasChild) {
         writeFieldValue(w, ctx, val, val.getClass(), ip, indent + 1);
         writeIndent(w, indent);
         w.writeEndElement();
      }
   }

   // -----------------------------------------------------------------------------
   // Methods
   // -----------------------------------------------------------------------------

   private static void buildMethods(XMLStreamWriter w, DumpContext ctx, Class<?> clazz,
         boolean ip, int indent) throws Exception {
      Set<String>    seen   = new HashSet<String>();
      List<Object[]> groups = new ArrayList<Object[]>();

      for (Class<?> c : hierarchy(clazz, false)) {
         if (c == Object.class || isIgnoredClass(c, ctx)) continue;
         List<Method> methods = collectGroupMethods(c, seen);
         if (!methods.isEmpty()) groups.add(new Object[]{c, c.isInterface() ? "interface" : "class", methods});
      }
      for (Class<?> iface : collectInterfaces(clazz)) {
         if (isIgnoredClass(iface, ctx)) continue;
         List<Method> methods = collectGroupMethods(iface, seen);
         if (!methods.isEmpty()) groups.add(new Object[]{iface, "interface", methods});
      }

      writeIndent(w, indent);
      if (groups.isEmpty()) { w.writeEmptyElement("methods"); return; }
      w.writeStartElement("methods");
      for (Object[] g : groups) {
         @SuppressWarnings("unchecked")
         List<Method> methods = (List<Method>) g[2];
         buildMethodGroup(w, (Class<?>) g[0], (String) g[1], methods, ip, indent + 1);
      }
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static List<Method> collectGroupMethods(Class<?> c, Set<String> seen) {
      List<Method> result = new ArrayList<Method>();
      for (Method m : c.getDeclaredMethods())
         if (!m.isSynthetic() && !m.isBridge() && seen.add(signature(m)))
            result.add(m);
      return result;
   }

   private static void buildMethodGroup(XMLStreamWriter w, Class<?> c, String kind,
         List<Method> methods, boolean ip, int indent) throws Exception {
      writeIndent(w, indent);
      w.writeStartElement("method-group");
      w.writeAttribute("declaringClass", c.getName());
      w.writeAttribute("kind",           kind);
      for (Method m : methods) buildMethod(w, m, ip, indent + 1);
      writeIndent(w, indent);
      w.writeEndElement();
   }

   private static void buildMethod(XMLStreamWriter w, Method m,
         boolean ip, int indent) throws Exception {
      Parameter[] ps      = ip ? m.getParameters()     : new Parameter[0];
      Class<?>[]  exTypes = ip ? m.getExceptionTypes() : new Class[0];
      boolean     hasChild = ps.length > 0 || exTypes.length > 0;

      writeIndent(w, indent);
      if (hasChild) w.writeStartElement("method");
      else          w.writeEmptyElement("method");

      w.writeAttribute("name",       m.getName());
      w.writeAttribute("returnType", rawName(m.getGenericReturnType()));
      w.writeAttribute("modifiers",  Modifier.toString(m.getModifiers()));

      if (hasChild) {
         if (ps.length > 0) {
            writeIndent(w, indent + 1);
            w.writeStartElement("parameters");
            for (int i = 0; i < ps.length; i++) {
               writeIndent(w, indent + 2);
               w.writeEmptyElement("parameter");
               w.writeAttribute("index", String.valueOf(i));
               w.writeAttribute("name",  ps[i].getName());
               w.writeAttribute("type",  rawName(ps[i].getParameterizedType()));
            }
            writeIndent(w, indent + 1);
            w.writeEndElement();
         }
         if (exTypes.length > 0) {
            writeIndent(w, indent + 1);
            w.writeStartElement("exceptions");
            for (Class<?> ex : exTypes) {
               writeIndent(w, indent + 2);
               w.writeEmptyElement("exception");
               w.writeAttribute("type", ex.getName());
            }
            writeIndent(w, indent + 1);
            w.writeEndElement();
         }
         writeIndent(w, indent);
         w.writeEndElement();
      }
   }

   // -----------------------------------------------------------------------------
   // Stateless utilities
   // -----------------------------------------------------------------------------


   private static List<Class<?>> hierarchy(Class<?> clazz, boolean superFirst) {
      List<Class<?>> list = new ArrayList<Class<?>>();
      for (Class<?> c = clazz; c != null; c = c.getSuperclass()) list.add(c);
      if (superFirst) Collections.reverse(list);
      return list;
   }

   private static List<Field> declaredFields(Class<?> c, boolean statics) {
      List<Field> result = new ArrayList<Field>();
      for (Field f : c.getDeclaredFields())
         // Synthetic fields included: anonymous/inner classes store captured
         // variables (val$xxx, this$0) as synthetic fields.
         if (Modifier.isStatic(f.getModifiers()) == statics)
            result.add(f);
      return result;
   }

   /** Returns the raw (erased) class name for a Type, stripping generic parameters. */
   private static String rawName(Type type) {
      if (type == null)                              return "?";
      if (type instanceof Class)                     return ((Class<?>) type).getName();
      if (type instanceof ParameterizedType)         return rawName(((ParameterizedType) type).getRawType());
      if (type instanceof java.lang.reflect.TypeVariable)     return ((java.lang.reflect.TypeVariable<?>) type).getName();
      if (type instanceof java.lang.reflect.GenericArrayType) return rawName(((java.lang.reflect.GenericArrayType) type).getGenericComponentType()) + "[]";
      return type.toString();
   }

   /**
    * Returns the full generic type name for a Type, Java-8-safe
    * (replaces Type.getTypeName() which is Java 8+ but may not be available in all JVMs).
    */
   private static String typeName(Type type) {
      if (type == null) return "?";
      if (type instanceof Class) {
         Class<?> cls = (Class<?>) type;
         return cls.isArray() ? typeName(cls.getComponentType()) + "[]" : cls.getName();
      }
      if (type instanceof ParameterizedType) {
         ParameterizedType pt   = (ParameterizedType) type;
         StringBuilder     sb   = new StringBuilder(rawName(pt.getRawType()));
         Type[]            args = pt.getActualTypeArguments();
         if (args.length > 0) {
            sb.append('<');
            for (int i = 0; i < args.length; i++) {
               if (i > 0) sb.append(", ");
               sb.append(typeName(args[i]));
            }
            sb.append('>');
         }
         return sb.toString();
      }
      if (type instanceof java.lang.reflect.TypeVariable)
         return ((java.lang.reflect.TypeVariable<?>) type).getName();
      if (type instanceof java.lang.reflect.WildcardType) {
         java.lang.reflect.WildcardType wt    = (java.lang.reflect.WildcardType) type;
         Type[]                         lower = wt.getLowerBounds();
         Type[]                         upper = wt.getUpperBounds();
         if (lower.length > 0)                              return "? super "   + typeName(lower[0]);
         if (upper.length > 0 && upper[0] != Object.class) return "? extends " + typeName(upper[0]);
         return "?";
      }
      if (type instanceof java.lang.reflect.GenericArrayType)
         return typeName(((java.lang.reflect.GenericArrayType) type).getGenericComponentType()) + "[]";
      return type.toString();
   }

   private static String signature(Method m) {
      return m.getName() + Arrays.toString(m.getParameterTypes());
   }

   private static boolean isLeaf(Object obj) {
      if (obj instanceof Number || obj instanceof Boolean
            || obj instanceof Character || obj instanceof String
            || obj instanceof Enum<?>)
         return true;
      String cls = obj.getClass().getName();
      return cls.startsWith("java.time.")
          || cls.startsWith("java.math.")
          || cls.equals("java.util.Date")
          || cls.equals("java.util.UUID");
   }

   private static boolean isIgnoredClass(Class<?> c, DumpContext ctx) {
      String name = c.getName();
      for (String prefix : ctx.ignoredPrefixes)
         if (name.startsWith(prefix)) return true;
      return false;
   }

   private static String leafString(Object obj) {
      if (obj instanceof Enum<?>) return ((Enum<?>) obj).name();
      return safeToString(obj);
   }
}

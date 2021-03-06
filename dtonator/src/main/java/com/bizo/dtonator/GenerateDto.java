package com.bizo.dtonator;

import static com.bizo.dtonator.Names.mapperFieldName;
import static com.bizo.dtonator.Names.mapperInterface;
import static com.bizo.dtonator.Names.simple;
import static joist.sourcegen.Argument.arg;
import static joist.util.Copy.list;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.apache.commons.lang.StringUtils.uncapitalize;

import java.util.List;

import com.bizo.dtonator.config.DtoConfig;
import com.bizo.dtonator.config.DtoProperty;
import com.bizo.dtonator.config.RootConfig;
import com.bizo.dtonator.properties.GenericParser;
import com.bizo.dtonator.properties.GenericPartsDto;

import joist.sourcegen.Argument;
import joist.sourcegen.GClass;
import joist.sourcegen.GDirectory;
import joist.sourcegen.GMethod;
import joist.util.Copy;
import joist.util.Join;

public class GenerateDto {

  private final GDirectory out;
  private final GClass mapper;
  private final RootConfig config;
  private final List<String> takenToDtoOverloads;
  private final DtoConfig dto;
  private final GClass gc;

  public GenerateDto(
    final RootConfig config,
    final GDirectory out,
    final GClass mapper,
    final List<String> takenToDtoOverloads,
    final DtoConfig dto) {
    this.config = config;
    this.out = out;
    this.mapper = mapper;
    this.takenToDtoOverloads = takenToDtoOverloads;
    this.dto = dto;
    String dtoType = dto.getDtoType();

    String typeStr = dto.getClassTypesString();

    if (typeStr != null && !typeStr.isEmpty()) {
      dtoType = dtoType + "<" + typeStr + ">";
    }

    gc = out.getClass(dtoType);
  }

  public void generate() {
    System.out.println("Generating " + dto.getSimpleName());
    addBaseClassIfNeeded();
    addAnnotations();
    addInterfaces();
    addCopyOfMethod();
    addDtoFields();
    addDefaultConstructor();
    addFullConstructor();
    addCopyMethod();
    addEqualityIfNeeded();
    addToString();
    addToFromMethodsToMapperIfNeeded();
    createMapperTypeIfNeeded();
    makeAbstractIfNeeded();
  }

  private void addBaseClassIfNeeded() {
    if (dto.getBaseDtoSimpleNameWithGenerics() != null) {
      gc.baseClassName(dto.getBaseDtoSimpleNameWithGenerics());
    }
  }

  private void addAnnotations() {
    gc.addAnnotation("@javax.annotation.Generated(\"dtonator\")");
    for (final String annotation : dto.getAnnotations()) {
      gc.addAnnotation(annotation);
    }
  }

  private void addInterfaces() {
    for (final String i : dto.getInterfaces()) {
      gc.implementsInterface(i);
    }
  }

  private void addDtoFields() {
    for (final DtoProperty dp : dto.getClassProperties()) {
      gc.getField(dp.getName()).setPublic().type(dp.getDtoType());
      if (dto.includeBeanMethods()) {
        gc.addGetterSetter(dp.getDtoType(), dp.getName());
      }
    }
  }

  private void addDefaultConstructor() {
    final GMethod cstr0 = gc.getConstructor();
    if (dto.shouldAddPublicConstructor()) {
      // keep public
      for (final DtoProperty dp : dto.getClassProperties()) {
        if (dp.getDtoType().startsWith("java.util.ArrayList") || dp.getDtoType().startsWith("java.util.HashSet")) {
          cstr0.body.line("this.{} = new {}();", dp.getName(), dp.getDtoType());
        }
      }
    } else {
      cstr0.setProtected();
    }
  }

  private void addFullConstructor() {
    final List<Argument> typeAndNames = list();
    for (final DtoProperty dp : dto.getAllPropertiesMap().values()) {
      typeAndNames.add(arg(dp.getDtoType(), dp.getName()));
    }
    final GMethod cstr = gc.getConstructor(typeAndNames);
    final List<String> superParams = list();
    for (DtoProperty dp : dto.getInheritedProperties()) {
      superParams.add(dp.getName());
    }
    cstr.body.line("super({});", Join.commaSpace(superParams));
    for (final DtoProperty dp : dto.getClassProperties()) {
      cstr.body.line("this.{} = {};", dp.getName(), dp.getName());
    }
  }

  private void addCopyOfMethod() {
    final GMethod m = gc.getMethod("copyOf", Argument.arg(dto.getDtoType(), "o")).returnType(dto.getDtoType()).setStatic();
    if (dto.getGenericTypeParameters() != null && !dto.isChildClass()) {
      m.typeParameters(dto.getGenericTypeParametersString());
    }
    // if there are subclasses, we'll have to probe which type of subclass to create
    List<DtoConfig> allTypes = Copy.list(dto.getSubClassDtos()).with(dto);
    boolean hasMoreThanOneType = allTypes.size() > 1;
    for (DtoConfig c : allTypes) {
      if (c.isAbstract()) {
        continue;
      }
      if (hasMoreThanOneType) {
        m.body.line("if (o instanceof {}) {", c.getDtoType());
      }

      // first make copies of children if needed      
      for (final DtoProperty dp : c.getAllPropertiesMap().values()) {
        if (dp.isListOfDtos()) {
          gc.stripAndImportPackageIfPossible("java.util.ArrayList");
          m.body.line("_ ArrayList<{}> {}Copy = new ArrayList<{}>();", dp.getSingleDto(), dp.getName(), dp.getSingleDto());
          m.body.line("_ for ({} e : (({}) o).{}) {", dp.getSingleDto(), c.getDtoType(), dp.getName());
          if (dp.getSingleDto().isEnum()) {
            m.body.line("_ _ {}Copy.add(e);", dp.getName());
          } else {
            m.body.line("_ _ {}Copy.add({}.copyOf(e));", dp.getName(), dp.getSingleDto());
          }
          m.body.line("_ }");
        } else if (dp.isSetOfDtos()) {
          gc.stripAndImportPackageIfPossible("java.util.HashSet");
          m.body.line("_ HashSet<{}> {}Copy = new HashSet<{}>();", dp.getSingleDto(), dp.getName(), dp.getSingleDto());
          m.body.line("_ for ({} e : (({}) o).{}) {", dp.getSingleDto(), c.getDtoType(), dp.getName());
          if (dp.getSingleDto().isEnum()) {
            m.body.line("_ _ {}Copy.add(e);", dp.getName());
          } else {
            m.body.line("_ _ {}Copy.add({}.copyOf(e));", dp.getName(), dp.getSingleDto());
          }
          m.body.line("_ }");
        }
      }
      // now call the constructor
      m.body.line("_ return new {}(", c.getDtoType());
      for (final DtoProperty dp : c.getAllPropertiesMap().values()) {
        if (dp.isListOfDtos() || dp.isSetOfDtos()) {
          m.body.line("_ _ {}Copy,", dp.getName());
        } else if (dp.isDto()) {
          m.body.line("_ _ {}.copyOf((({}) o).{}),", dp.getDtoType(), c.getDtoType(), dp.getName());
        } else {
          m.body.line("_ _ (({}) o).{},", c.getDtoType(), dp.getName());
        }
      }
      m.body.stripLastCharacterOnPreviousLine();
      m.body.line("_ _ );");
      if (hasMoreThanOneType) {
        m.body.line("}");
      }
    }
    if (hasMoreThanOneType) {
      m.body.line("throw new IllegalStateException(\"Unreachable.\");");
    }
  }

  private void addCopyMethod() {
    GMethod m = gc.getMethod("copy").returnType(dto.getDtoType());
    m.body.line("return {}.copyOf(this);", dto.getDtoType());
  }

  private void addEqualityIfNeeded() {
    // optionally generate equals + hashCode
    final List<String> eq = dto.getEquality();
    if (eq != null) {
      gc.addEquals(eq).addHashCode(eq);
    }
  }

  private void addToString() {
    final List<String> fieldNames = list();
    if (dto.getEquality() != null) {
      fieldNames.addAll(dto.getEquality());
    } else {
      for (DtoProperty dp : dto.getAllPropertiesMap().values()) {
        fieldNames.add(dp.getName());
      }
    }
    gc.addToString(fieldNames);
  }

  private void createMapperTypeIfNeeded() {
    if (!dto.requiresMapperType()) {
      return;
    }

    String dtoType = mapperInterface(config, dto);
    if (dto.getClassTypesString() != null) {
      dtoType = dtoType + "<" + dto.getClassTypesString() + ">";
    }
    final GClass mb = out.getClass(dtoType).setInterface();

    if (dto.getGenericClassTypes() != null && !dto.getGenericClassTypes().isEmpty()) {
      for (GenericPartsDto type : dto.getGenericClassTypes()) {

        mb.stripAndImportPackageIfPossible(type.getBoundClassString());
      }
    }

    for (final DtoProperty p : dto.getClassProperties()) {
      if (!p.isExtension()) {
        continue;
      }
      final String niceName = uncapitalize(simple(dto.getDomainType()));
      // add get{propertyName}
      mb.getMethod(extensionGetter(p), arg("Mapper", "m"), arg(dto.getDomainType(), niceName)).returnType(p.getDtoType());
      if (!p.isReadOnly()) {
        // add set{propertyName}
        mb.getMethod(extensionSetter(p), arg("Mapper", "m"), arg(dto.getDomainType(), niceName), arg(p.getDtoType(), p.getName()));
      }
    }
  }

  private void addToFromMethodsToMapperIfNeeded() {
    if (dto.isManualDto()) {
      return;
    }
    addToDtoMethodToMapper();
    addToDtoOverloadToMapperIfAble();
    addFromDtoMethodToMapper();
    if (dto.hasIdProperty()) {
      addFromOnlyDtoMethodToMapper();
    }
  }

  /** Adds {@code mapper.toXxxDto(Domain)}. */
  private void addToDtoMethodToMapper() {
    final GMethod toDto = mapper.getMethod("to" + dto.getSimpleName(), arg(dto.getDomainType(), "o"));
    toDto.returnType(dto.getDtoType());
    toDto.body.line("if (o == null) {");
    toDto.body.line("_ return null;");
    toDto.body.line("}");
    for (DtoConfig subClass : dto.getSubClassDtos()) {
      toDto.body.line("if (o instanceof {}) {", subClass.getDomainType());
      toDto.body.line("_ return to{}(({}) o);", subClass.getSimpleName(), subClass.getDomainType());
      toDto.body.line("}");
    }
    if (dto.isAbstract()) {
      toDto.body.line("throw new IllegalArgumentException(o + \" should be a subclass\");");
      return;
    }
    toDto.body.line("return new {}(", dto.getDtoType());
    for (final DtoProperty dp : dto.getAllPropertiesMap().values()) {
      if (dp.isExtension()) {
        // delegate to the user's mapper method for this property
        toDto.body.line("_ {}.{}(this, o),", mapperFieldName(dp.getDto()), extensionGetter(dp));
      } else if (dp.getGetterMethodName() == null) {

        toDto.body.line("_ null,");
      } else if (dp.isValueType()) {
        // delegate to the user type mapper for this property
        toDto.body.line(
          "_ o.{}() == null ? null : {}.toDto(o.{}()),",
          dp.getGetterMethodName(),
          mapperFieldName(dp.getValueTypeConfig()),
          dp.getGetterMethodName());
      } else if (dp.isEnum()) {
        // delegate to the enum converter
        toDto.body.line("_ toDto(o.{}()),", dp.getGetterMethodName());
      } else if (dp.isChainedId()) {
        toDto.body.line("_ o.{}() == null ? null : o.{}().getId(),", dp.getGetterMethodName(), dp.getGetterMethodName()); // assume getId
      } else if (dp.isEntity()) {
        // delegate to the entity's toDto converter
        toDto.body.line("_ toDto(o.{}()),", dp.getGetterMethodName());
      } else if (dp.isListOfEntities()) {
        // make and delegate to a method to convert the entities to dtos
        toDto.body.line("_ {}For{}(o.{}()),", dp.getName(), dto.getSimpleName(), dp.getGetterMethodName());
        final GMethod c = mapper.getMethod(dp.getName() + "For" + dto.getSimpleName(), arg(dp.getDomainType(), "os"));
        c.returnType(dp.getDtoType()).setPrivate();
        // assumes dto type can be instantiated
        c.body.line("if (os == null) {");
        c.body.line("_ return null;");
        c.body.line("}");
        String collectionType = dp.getDtoType();
        if (dp.getDtoType().startsWith("List")) {
          collectionType = dp.getDtoType().replaceFirst("List", "ArrayList");
        } else if (dp.getDtoType().startsWith("java.util.List")) {
          collectionType = dp.getDtoType().replaceFirst("java.util.List", "java.util.ArrayList");
        }
        c.body.line("{} dtos = new {}();", dp.getDtoType(), collectionType);
        c.body.line("for ({} o : os) {", dp.getSingleDomainType());
        c.body.line("_ dtos.add(to{}(o));", dp.getSimpleSingleDtoType());
        c.body.line("}");
        c.body.line("return dtos;");
      } else if (dp.isSetOfEntities()) {
        // make and delegate to a method to convert the entities to dtos
        toDto.body.line("_ {}For{}(o.{}()),", dp.getName(), dto.getSimpleName(), dp.getGetterMethodName());
        final GMethod c = mapper.getMethod(dp.getName() + "For" + dto.getSimpleName(), arg(dp.getDomainType(), "os"));
        c.returnType(dp.getDtoType()).setPrivate();
        // assumes dto type can be instantiated
        c.body.line("if (os == null) {");
        c.body.line("_ return null;");
        c.body.line("}");
        String collectionType = dp.getDtoType();
        if (dp.getDtoType().startsWith("Set")) {
          collectionType = dp.getDtoType().replaceFirst("Set", "HashSet");
        } else if (dp.getDtoType().startsWith("java.util.Set")) {
          collectionType = dp.getDtoType().replaceFirst("java.util.Set", "java.util.HashSet");
        }
        c.body.line("{} dtos = new {}();", dp.getDtoType(), collectionType);
        c.body.line("for ({} o : os) {", dp.getSingleDomainType());
        c.body.line("_ dtos.add(to{}(o));", dp.getSimpleSingleDtoType());
        c.body.line("}");
        c.body.line("return dtos;");
      } else {
        // do a straight get
        toDto.body.line("_ o.{}(),", dp.getGetterMethodName());
      }
    }
    toDto.body.stripLastCharacterOnPreviousLine();
    toDto.body.line(");");
  }

  /** Adds {@code mapper.toDto(domain)} (no "Xxx") if the overload isn't taken yet. */
  private void addToDtoOverloadToMapperIfAble() {
    if (!takenToDtoOverloads.contains(dto.getDomainType())) {
      final GMethod toDtoOverload = mapper.getMethod("toDto", arg(dto.getDomainType(), "o"));
      toDtoOverload.returnType(dto.getDtoType());
      toDtoOverload.body.line("return to{}(o);", dto.getSimpleName());
      takenToDtoOverloads.add(dto.getDomainType());
    }
  }

  /** Adds {@code mapper.fromDto(domain, dto)}, the client is responsible for finding {@code domain}. */
  private void addFromDtoMethodToMapper() {
    final GMethod fromDto = mapper.getMethod(
      "fromDto", //
      arg(dto.getDomainType(), "o"),
      arg(dto.getDtoType(), "dto"));
    fromDto.body.line("DomainObjectContext c = DomainObjectContext.push();");
    fromDto.body.line("try {");
    for (final DtoProperty dp : dto.getClassProperties()) {
      if (dp.isReadOnly()) {
        continue;
      }
      // given we already have an instance of o, assume we shouldn't change the id
      if ("id".equals(dp.getName())) {
        continue;
      }
      if (dp.isExtension()) {
        fromDto.body.line("_ {}.{}(this, o, dto.{});", mapperFieldName(dto), extensionSetter(dp), dp.getName());
      } else if (dp.isValueType()) {
        fromDto.body.line(
          "_ o.{}(dto.{} == null ? null : {}.fromDto(dto.{}));", //
          dp.getSetterMethodName(),
          dp.getName(),
          mapperFieldName(dp.getValueTypeConfig()),
          dp.getName());
      } else if (dp.isEnum()) {
        fromDto.body.line("_ o.{}(fromDto(dto.{}));", dp.getSetterMethodName(), dp.getName());
      } else if (dp.isChainedId()) {
        fromDto.body.line("_ if (dto.{} != null) {", dp.getName());
        fromDto.body.line("_ _ o.{}(lookup.lookup({}.class, dto.{}));", dp.getSetterMethodName(), dp.getDomainType(), dp.getName());
        fromDto.body.line("_ } else {");
        fromDto.body.line("_ _ o.{}(null);", dp.getSetterMethodName());
        fromDto.body.line("_ }");
      } else if (dp.isEntity()) {
        fromDto.body.line("_ o.{}(fromDto(dto.{}));", dp.getSetterMethodName(), dp.getName());
      } else if (dp.isListOfEntities()) {
        final String helperMethod = dp.getName() + "From" + dto.getSimpleName();
        fromDto.body.line("_ o.{}({}(dto.{}));", dp.getSetterMethodName(), helperMethod, dp.getName());
        final GMethod c = mapper.getMethod(helperMethod, arg(dp.getDtoType(), "dtos"));
        c.returnType(dp.getDomainType()).setPrivate();
        // assumes List->ArrayList
        c.body.line("if (dtos == null) {");
        c.body.line("_ return null;");
        c.body.line("}");
        c.body.line("{} os = new {}();", dp.getDomainType(), dp.getDomainType().replace("List", "ArrayList"));
        c.body.line("for ({} dto : dtos) {", dp.getSingleDtoType());
        c.body.line("_ os.add(fromDto(dto));");
        c.body.line("}");
        c.body.line("return os;");
      } else if (dp.isSetOfEntities()) {
        final String helperMethod = dp.getName() + "From" + dto.getSimpleName();
        fromDto.body.line("_ o.{}({}(dto.{}));", dp.getSetterMethodName(), helperMethod, dp.getName());
        final GMethod c = mapper.getMethod(helperMethod, arg(dp.getDtoType(), "dtos"));
        c.returnType(dp.getDomainType()).setPrivate();
        // assumes Set->HashSet
        c.body.line("if (dtos == null) {");
        c.body.line("_ return null;");
        c.body.line("}");
        c.body.line("{} os = new {}();", dp.getDomainType(), dp.getDomainType().replace("Set", "HashSet"));
        c.body.line("for ({} dto : dtos) {", dp.getSingleDtoType());
        c.body.line("_ os.add(fromDto(dto));");
        c.body.line("}");
        c.body.line("return os;");
      } else if (dp.isGenericType()) {
        fromDto.body.line("_ o.{}(fromDto(dto.{}));", dp.getSetterMethodName(), dp.getName());
      }

      else {
        fromDto.body.line("_ o.{}(dto.{});", dp.getSetterMethodName(), dp.getName());
      }
    }
    if (dto.getBaseDto() != null) {
      fromDto.body.line("_ fromDto(o, ({}) dto);", dto.getBaseDto().getDtoType());
    }
    fromDto.body.line("} finally {");
    fromDto.body.line("_ c.pop();");
    fromDto.body.line("}");
  }

  /** Adds {@code mapper.fromDto(dto)}, using the {@code id} and {@link DomainObjectLookup}. */
  private void addFromOnlyDtoMethodToMapper() {
    final GMethod fromDto = mapper.getMethod("fromDto", arg(dto.getDtoType(), "dto"));
    fromDto.returnType(dto.getDomainType());
    fromDto.body.line("DomainObjectContext c = DomainObjectContext.push();");
    fromDto.body.line("try {");
    fromDto.body.line("_ if (dto == null) {");
    fromDto.body.line("_ _ return null;");
    fromDto.body.line("_ }");
    for (DtoConfig subClass : dto.getSubClassDtos()) {
      fromDto.body.line("_ if (dto instanceof {}) {", subClass.getDtoType());
      fromDto.body.line("_ _ return fromDto(({}) dto);", subClass.getDtoType());
      fromDto.body.line("_ }");
    }
    if (dto.isAbstract()) {
      fromDto.body.line("_ throw new IllegalArgumentException(dto + \" must be a subclass because " + dto.getDomainType() + " is abstract\");");
    } else {
      fromDto.body.line("_ final {} o;", dto.getDomainType());
      fromDto.body.line("_ if (dto.id != null) {");
      fromDto.body.line("_ _ o = lookup.lookup({}.class, dto.id);", dto.getDomainType());
      fromDto.body.line("_ } else if (c.get(dto) != null) {");
      fromDto.body.line("_ _ o = ({}) c.get(dto);", dto.getDomainType());
      fromDto.body.line("_ } else {");
      fromDto.body.line("_ _ o = new {}();", dto.getDomainType());
      fromDto.body.line("_ _ c.store(dto, o);");
      fromDto.body.line("_ }");
      fromDto.body.line("_ fromDto(o, dto);");
      fromDto.body.line("_ return o;");
    }
    fromDto.body.line("} finally {");
    fromDto.body.line("_ c.pop();");
    fromDto.body.line("}");
  }

  private void makeAbstractIfNeeded() {
    if (dto.isAbstract()) {
      gc.setAbstract();
    }
  }

  private static String extensionGetter(final DtoProperty p) {
    return "get" + capitalize(p.getName());
  }

  private static String extensionSetter(final DtoProperty p) {
    return "set" + capitalize(p.getName());
  }

}

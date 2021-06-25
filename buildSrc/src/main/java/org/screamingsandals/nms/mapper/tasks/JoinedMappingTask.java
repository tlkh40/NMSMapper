package org.screamingsandals.nms.mapper.tasks;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.VersionNumber;
import org.screamingsandals.nms.mapper.joined.JoinedClassDefinition;
import org.screamingsandals.nms.mapper.single.ClassDefinition;
import org.screamingsandals.nms.mapper.single.MappingType;
import org.screamingsandals.nms.mapper.utils.UtilsHolder;

import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class JoinedMappingTask extends DefaultTask {
    private static MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("sha256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Input
    public abstract Property<UtilsHolder> getUtils();

    @TaskAction
    public void run() {
        System.out.println("Generating joined mapping...");

        var versions = getUtils().get().getNewlyGeneratedMappings().stream().sorted(Comparator.comparing(VersionNumber::parse).reversed()).collect(Collectors.toList());

        var mappings = getUtils().get().getMappings();

        var finalMapping = getUtils().get().getJoinedMappings();
        versions.forEach(version -> {
            mappings.get(version).forEach((key, classDefinition) -> {
                try {
                    var finalClassName = getJoinedClassName(classDefinition);
                    if (!finalMapping.containsKey(finalClassName)) {
                        finalMapping.put(finalClassName, new JoinedClassDefinition());
                    }
                    var definition = finalMapping.get(finalClassName);
                    classDefinition.getMapping().forEach((mappingType, s) -> definition.getMapping()
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getValue().equals(s) && entry.getKey().getValue() == mappingType)
                            .findFirst()
                            .ifPresentOrElse(entry -> {
                                definition.getMapping().remove(entry.getKey());
                                definition.getMapping().put(Map.entry(entry.getKey().getKey() + "," + version, entry.getKey().getValue()), entry.getValue());
                            }, () -> definition.getMapping().put(Map.entry(version, mappingType), s)));

                    classDefinition.getConstructors().forEach(constructorDefinition -> definition.getConstructors()
                            .stream()
                            .filter(joinedConstructor -> constructorDefinition.getParameters()
                                    .stream()
                                    .map(link -> remapParameterType(version, link))
                                    .collect(Collectors.toList())
                                    .equals(joinedConstructor.getParameters())
                            )
                            .findFirst()
                            .ifPresentOrElse(joinedConstructor -> joinedConstructor.getSupportedVersions().add(version), () -> {
                                var constructor = new JoinedClassDefinition.JoinedConstructor();
                                constructor.getSupportedVersions().add(version);
                                constructor.getParameters().addAll(constructorDefinition.getParameters()
                                        .stream()
                                        .map(link -> remapParameterType(version, link))
                                        .collect(Collectors.toList()));
                                definition.getConstructors().add(constructor);
                            }));

                    classDefinition.getFields().forEach((s, fieldDefinition) -> {
                        definition.getFields()
                                .stream()
                                .filter(joinedField -> joinedField.getType().equals(remapParameterType(version, fieldDefinition.getType())) && joinedField.getMapping()
                                        .entrySet()
                                        .stream()
                                        .filter(entry -> entry.getKey().getValue() == MappingType.MOJANG)
                                        .map(Map.Entry::getValue)
                                        .findFirst()
                                        .orElse("")
                                        .equals(fieldDefinition.getMapping().get(MappingType.MOJANG)))
                                .findFirst()
                                .ifPresentOrElse(joinedField -> fieldDefinition.getMapping()
                                        .forEach((mappingType, s3) -> joinedField.getMapping()
                                                .entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().equals(s3) && entry.getKey().getValue() == mappingType)
                                                .findFirst()
                                                .ifPresentOrElse(entry -> {
                                                            joinedField.getMapping().remove(entry.getKey());
                                                            joinedField.getMapping().put(Map.entry(entry.getKey().getKey() + "," + version, entry.getKey().getValue()), entry.getValue());
                                                        },
                                                        () -> joinedField.getMapping().put(Map.entry(version, mappingType), s3))), () -> {
                                    var joinedField = new JoinedClassDefinition.JoinedField(remapParameterType(version, fieldDefinition.getType()));
                                    fieldDefinition.getMapping()
                                            .forEach((mappingType, s1) -> joinedField.getMapping().put(Map.entry(version, mappingType), s1));

                                    definition.getFields().add(joinedField);
                                });
                    });


                    classDefinition.getMethods().forEach(methodDefinition -> {
                        definition.getMethods()
                                .stream()
                                .filter(joinedMethod -> joinedMethod.getReturnType().equals(remapParameterType(version, methodDefinition.getReturnType()))
                                        && joinedMethod.getMapping()
                                        .entrySet()
                                        .stream()
                                        .filter(entry -> entry.getKey().getValue() == MappingType.MOJANG)
                                        .map(Map.Entry::getValue)
                                        .findFirst()
                                        .orElse("")
                                        .equals(methodDefinition.getMapping().get(MappingType.MOJANG))
                                        && methodDefinition.getParameters()
                                        .stream()
                                        .map(link -> remapParameterType(version, link))
                                        .collect(Collectors.toList())
                                        .equals(joinedMethod.getParameters()))
                                .findFirst()
                                .ifPresentOrElse(joinedMethod -> methodDefinition.getMapping()
                                        .forEach((mappingType, s3) -> joinedMethod.getMapping()
                                                .entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().equals(s3) && entry.getKey().getValue() == mappingType)
                                                .findFirst()
                                                .ifPresentOrElse(entry -> {
                                                            joinedMethod.getMapping().remove(entry.getKey());
                                                            joinedMethod.getMapping().put(Map.entry(entry.getKey().getKey() + "," + version, entry.getKey().getValue()), entry.getValue());
                                                        },
                                                        () -> joinedMethod.getMapping().put(Map.entry(version, mappingType), s3))), () -> {
                                    var joinedMethod = new JoinedClassDefinition.JoinedMethod(remapParameterType(version, methodDefinition.getReturnType()));
                                    methodDefinition.getMapping()
                                            .forEach((mappingType, s1) -> joinedMethod.getMapping().put(Map.entry(version, mappingType), s1));
                                    joinedMethod.getParameters().addAll(methodDefinition.getParameters()
                                            .stream()
                                            .map(link -> remapParameterType(version, link))
                                            .collect(Collectors.toList()));

                                    definition.getMethods().add(joinedMethod);
                                });
                    });
                } catch (DigestException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public String getJoinedClassName(ClassDefinition classDefinition) throws DigestException {
        if (classDefinition.getJoinedKey() != null) {
            return classDefinition.getJoinedKey();
        }

        var mojMap = classDefinition.getMapping().get(MappingType.MOJANG); // TODO: add some resolution algorithm for < 1.14.4

        var links = getUtils().get().getJoinedMappingsClassLinks();

        if (links.containsKey(mojMap)) {
            classDefinition.setJoinedKey(links.get(mojMap));
            return links.get(mojMap);
        }

        var byteArray = digest.digest(mojMap.getBytes(StandardCharsets.UTF_8));
        var longHash = new StringBuilder();

        for (var b : byteArray) {
            longHash.append(Integer.toHexString(0xFF & b));
        }

        var length = 7;

        while (true) {
            var hash = "c_" + longHash.substring(0, length);

            if (links.containsValue(hash)) {
                length++;
            } else {
                classDefinition.setJoinedKey(hash);
                links.put(mojMap, hash);
                return hash;
            }
        }
    }

    @SneakyThrows
    public ClassDefinition.Link remapParameterType(String version, ClassDefinition.Link link) {
        if (link.isNms()) {
            var type = link.getType();
            var suffix = new StringBuilder();
            while (type.endsWith("[]")) {
                suffix.append("[]");
                type = type.substring(0, type.length() - 2);
            }
            if (type.matches(".*\\$\\d+")) { // WTF? How
                suffix.insert(0, type.substring(type.lastIndexOf("$")));
                type = type.substring(0, type.lastIndexOf("$"));
            }
            return ClassDefinition.Link.nmsLink(getJoinedClassName(getUtils().get().getMappings().get(version).get(type)) + suffix);
        }
        return link;
    }
}

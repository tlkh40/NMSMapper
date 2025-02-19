/*
 * Copyright 2022 ScreamingSandals
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.screamingsandals.nms.mapper.tasks;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.screamingsandals.nms.mapper.extension.Version;
import org.screamingsandals.nms.mapper.parser.*;
import org.screamingsandals.nms.mapper.single.MappingType;
import org.screamingsandals.nms.mapper.utils.ErrorsLogger;
import org.screamingsandals.nms.mapper.utils.License;
import org.screamingsandals.nms.mapper.utils.UtilsHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class RemappingTask extends DefaultTask {
    @Input
    public abstract Property<Version> getVersion();

    @Input
    public abstract Property<UtilsHolder> getUtils();

    @SneakyThrows
    @TaskAction
    public void run() {
        var version = getVersion().get();
        var utils = getUtils().get();

        var allMappings = new ArrayList<MappingType>();
        var mappings = utils.getMappings();
        var newlyGeneratedMappings = utils.getNewlyGeneratedMappings();
        var workspace = version.getWorkspace();

        var realVersion = version.getRealVersion() != null ? version.getRealVersion() : version.getVersion();

        System.out.println("======= Mapping " + realVersion + " =======");

        System.out.println("Getting base data from vanilla jar...");

        var entry = VanillaJarParser.map(workspace.getFile(version.getVanillaJar(), "minecraft_server.jar"));

        var mapping = entry.getKey();
        var excluded = entry.getValue();

        System.out.println(excluded.size() + " symbols (fields, methods) excluded from final mapping: synthetic");
        System.out.println(mapping.size() + " classes mapped");

        mappings.put(version.getVersion(), mapping);
        allMappings.add(MappingType.OBFUSCATED);

        var defaultMappings = MappingType.SPIGOT;

        var errors = new ErrorsLogger();

        if (version.getMojangMappings() != null && version.getMojangMappings().isPresent()) {
            System.out.println("Applying Mojang mappings...");
            defaultMappings = MappingType.MOJANG;
            allMappings.add(MappingType.MOJANG);

            var license = MojangMappingParser.map(
                    mapping,
                    workspace.getFile(version.getMojangMappings(), "mojang.mapping"),
                    excluded,
                    errors
            );

            errors.printWarn();
            errors.reset();

            if (license != null) {
                getUtils().get().getLicenses().put(Map.entry(version.getVersion(), MappingType.MOJANG), new License(license, List.of(version.getMojangMappings().getUrl())));
            }
        }

        if (version.getSeargeMappings() != null && version.getSeargeMappings().isPresent()) {
            System.out.println("Applying Searge (Forge) mappings...");

            var license = SeargeMappingParser.map(mapping, version, excluded, errors);
            allMappings.add(MappingType.SEARGE);

            errors.printWarn();
            errors.reset();

            if (license != null) {
                getUtils().get().getLicenses().put(Map.entry(version.getVersion(), MappingType.SEARGE), new License(license, List.of(version.getSeargeMappings().getUrl())));
            }
        }

        if (version.getSpigotClassMappings() != null && version.getSpigotClassMappings().isPresent()) {
            System.out.println("Applying Spigot mappings...");
            var license = SpigotMappingParser.mapTo(version, mapping, excluded, errors);
            allMappings.add(MappingType.SPIGOT);

            errors.printWarn();
            errors.reset();

            if (license != null) {
                List<String> links;
                if (version.getSpigotMemberMappings() != null && version.getSpigotMemberMappings().isPresent()) {
                    links = List.of(version.getSpigotClassMappings().getUrl(), version.getSpigotMemberMappings().getUrl());
                } else {
                    links = List.of(version.getSpigotClassMappings().getUrl());
                }
                getUtils().get().getLicenses().put(Map.entry(version.getVersion(), MappingType.SPIGOT), new License(license, links));
            }
        }

        if (version.getIntermediaryMappings() != null && version.getIntermediaryMappings().isPresent()) {
            System.out.println("Applying Intermediary mappings...");
            errors.setSilent(true); // it spams the console for no reason, maybe we will fix it later
            var license = IntermediaryMappingParser.map(mapping, version, excluded, errors);
            allMappings.add(MappingType.INTERMEDIARY);

            errors.printWarn();
            errors.reset();

            if (license != null) {
                getUtils().get().getLicenses().put(Map.entry(version.getVersion(), MappingType.INTERMEDIARY), new License(license, List.of(version.getIntermediaryMappings().getUrl())));
            }
        }

        // TODO: Yarn

        newlyGeneratedMappings.put(version.getVersion(), defaultMappings);
        utils.getAllMappingsByVersion().put(version.getVersion(), allMappings);
    }
}

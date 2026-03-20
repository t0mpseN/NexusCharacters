package net.tompsen.charsel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataFileManager {

    private final Path DATA_FILE_PATH = FabricLoader.getInstance()
            .getGameDir().resolve("charsel/characters.dat");

    public List<CharacterDto> characterList = new ArrayList<>();

    public DataFileManager() {
        init();
    }

    public void init() {
        if (Files.exists(DATA_FILE_PATH)) {
            characterList = read();
        } else {
            generate();
        }
    }

    public void generate() {
        try {
            Files.createDirectories(DATA_FILE_PATH.getParent());
            characterList = new ArrayList<>();
            save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<CharacterDto> read() {
        try {
            NbtCompound root = NbtIo.readCompressed(DATA_FILE_PATH, NbtSizeTracker.ofUnlimitedBytes());
            NbtList list = root.getList("characters", NbtElement.COMPOUND_TYPE);
            return list.stream()
                    .map(tag -> CharacterDto.fromNbt((NbtCompound) tag))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void save() {
        try {
            NbtCompound root = new NbtCompound();
            NbtList list = new NbtList();
            characterList.stream().map(CharacterDto::toNbt).forEach(list::add);
            root.put("characters", list);
            NbtIo.writeCompressed(root, DATA_FILE_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addCharacter(CharacterDto character) {
        characterList.add(character);
        save();
    }

    public void updateCharacter(CharacterDto updated) {
        characterList.replaceAll(c -> c.id().equals(updated.id()) ? updated : c);
        save();
    }

    public void deleteCharacter(UUID id) {
        characterList.removeIf(c -> c.id().equals(id));
        save();
    }

    public Optional<CharacterDto> findById(UUID id) {
        return characterList.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
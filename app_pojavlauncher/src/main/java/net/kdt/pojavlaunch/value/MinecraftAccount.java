package net.kdt.pojavlaunch.value;


import android.graphics.BitmapFactory;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.google.gson.*;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

@SuppressWarnings("IOStreamConstructor")
@Keep
public class MinecraftAccount {
    public String accessToken = "0"; // access token
    public String clientToken = "0"; // clientID: refresh and invalidate
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public String selectedVersion = "1.7.10";
    public boolean isMicrosoft = false;
    /**
     * Whether this account was obtained from Ely.by's Yggdrasil authentication server.
     * Such accounts carry a real {@link #accessToken}, but need the game to be pointed at
     * Ely.by with authlib-injector on launch, see
     * {@link net.kdt.pojavlaunch.authenticator.elyby.ElyByLaunchHelper}.
     */
    public boolean isElyBy = false;
    public String msaRefreshToken = "0";
    public String xuid;
    public long expiresAt;
    public String skinFaceBase64;
    private Bitmap mFaceCache;
    
    void updateSkinFace(String uuid) {
        try {
            File skinFile = getSkinFaceFile(username);
            Tools.downloadFile("https://mc-heads.net/head/" + uuid + "/100", skinFile.getAbsolutePath());
            
            Log.i("SkinLoader", "Update skin face success");
        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc...
            // Simply ignore updating skin face
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    /**
     * Fetches the head of an Ely.by player. mc-heads.net only knows about Mojang profiles, so
     * Ely.by skins are pulled from their skin system (Chrly) and the head is cropped out of the
     * full skin instead.
     */
    private void updateElyBySkinFace() {
        File skinFile = getSkinFaceFile(username);
        File textureFile = new File(Tools.DIR_CACHE, username + "_elyby_skin.png");
        try {
            Tools.downloadFile(ElyByConstants.skinTextureUrl(username), textureFile.getAbsolutePath());
            Bitmap skin = BitmapFactory.decodeFile(textureFile.getAbsolutePath());
            if (skin == null) throw new IOException("The Ely.by skin texture could not be decoded");
            // The head of every Minecraft skin, classic or slim, sits at (8, 0) and is 8x8 pixels
            Bitmap face = Bitmap.createBitmap(skin, 8, 0, 8, 8);
            Bitmap scaled = Bitmap.createScaledBitmap(face, 100, 100, false);
            try (OutputStream os = new FileOutputStream(skinFile)) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            mFaceCache = scaled;
            Log.i("SkinLoader", "Updated the Ely.by skin face of " + username);
        } catch (IOException | RuntimeException e) {
            // The player may simply not have a skin uploaded, that is not worth bothering them for
            Log.w("SkinLoader", "Could not update the Ely.by skin face", e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            textureFile.delete();
        }
    }

    /**
     * Offline ("local") accounts have no token at all. They are perfectly usable for single player
     * and for servers running with online-mode=false, they just can't use anything that requires
     * Mojang to recognize the player.
     */
    public boolean isLocal(){
        return accessToken.equals("0") && !username.startsWith("Demo.");
    }

    public boolean isDemo(){
        return username.startsWith("Demo.");
    }

    /**
     * @return the UUID an offline (non-authenticated) player is identified by, computed exactly
     * like the vanilla server does, so that player data stays consistent with other launchers
     * and with the {@code OfflinePlayer:<name>} entries servers already keep.
     */
    public static String getOfflinePlayerUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }
    
    public void updateSkinFace() {
        if (isElyBy) {
            updateElyBySkinFace();
            return;
        }
        updateSkinFace(profileId);
    }
    
    public String save(String outPath) throws IOException {
        Tools.write(outPath, Tools.GLOBAL_GSON.toJson(this));
        return username;
    }
    
    public String save() throws IOException {
        return save(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json");
    }
    
    public static MinecraftAccount parse(String content) throws JsonSyntaxException {
        return Tools.GLOBAL_GSON.fromJson(content, MinecraftAccount.class);
    }
    @Nullable
    public static MinecraftAccount load(String name) {
        if(!accountExists(name)) return null;
        try {
            MinecraftAccount acc = parse(Tools.read(Tools.DIR_ACCOUNT_NEW + "/" + name + ".json"));
            if (acc.accessToken == null) {
                acc.accessToken = "0";
            }
            if (acc.clientToken == null) {
                acc.clientToken = "0";
            }
            if (acc.profileId == null) {
                acc.profileId = "00000000-0000-0000-0000-000000000000";
            }
            if (acc.username == null) {
                acc.username = "0";
            }
            if (acc.selectedVersion == null) {
                acc.selectedVersion = "1.7.10";
            }
            if (acc.msaRefreshToken == null) {
                acc.msaRefreshToken = "0";
            }
            return acc;
        } catch(NullPointerException | IOException | JsonSyntaxException e) {
            Log.e(MinecraftAccount.class.getName(), "Caught an exception while loading the profile",e);
            return null;
        }
    }

    public Bitmap getSkinFace(){
        if(isLocal()) return null;

        File skinFaceFile = getSkinFaceFile(username);
        if (!skinFaceFile.exists()) {
            // Legacy version, storing the head inside the json as base 64
            if(skinFaceBase64 == null) return null;
            byte[] faceIconBytes = Base64.decode(skinFaceBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(faceIconBytes, 0, faceIconBytes.length);
        } else {
            if(mFaceCache == null) {
                mFaceCache = BitmapFactory.decodeFile(skinFaceFile.getAbsolutePath());
            }
        }

        return mFaceCache;
    }

    public static Bitmap getSkinFace(String username) {
        return BitmapFactory.decodeFile(getSkinFaceFile(username).getAbsolutePath());
    }

    private static File getSkinFaceFile(String username) {
        return new File(Tools.DIR_CACHE, username + ".png");
    }

    private static boolean accountExists(String username){
        return new File(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json").exists();
    }
}

/*
Copyright (c) 2012, Mushroom Hostage
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package me.exphc.PotionsPlus;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Formatter;
import java.lang.Byte;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.Material.*;
import org.bukkit.material.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.scheduler.*;
import org.bukkit.enchantments.*;
import org.bukkit.potion.*;
import org.bukkit.*;

import net.minecraft.server.v1_6_R2.CraftingManager;

import org.bukkit.craftbukkit.v1_6_R2.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.v1_6_R2.inventory.CraftItemStack;

class PotionsPlusListener implements Listener {
    PotionsPlus plugin;

    public PotionsPlusListener(PotionsPlus plugin) {
        this.plugin = plugin;

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // EntityPotion
    //      List list = Item.POTION.b(this) = MCP Item.potion.getEffects(potionDamage)
    // Event only fires if there are any effects:
    //      if (list != null && !list.isEmpty()) {
    /* TODO: needed?

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled=false) //true) 
    public void onPotionSplash(PotionSplashEvent event) {
        plugin.log.info("Splash  "+event);

        ThrownPotion potion = event.getPotion();

        plugin.log.info("pot = " + potion);

        Collection<PotionEffect> potionEffects = potion.getEffects();

        Collection<LivingEntity> hits = event.getAffectedEntities();

        for (LivingEntity hit: hits) {
            for (PotionEffect potionEffect: potionEffects) {
                plugin.log.info("pe = " + potionEffect);

                plugin.log.info(" amp="+potionEffect.getAmplifier());
                plugin.log.info(" dur="+potionEffect.getDuration());
                plugin.log.info(" type="+potionEffect.getType());
            }
        }
    }
    */
}

public class PotionsPlus extends JavaPlugin implements Listener {
    Logger logger = Logger.getLogger("Minecraft");


    public void onEnable() {
        //new PotionsPlusListener(this);

        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();


        HashMap effectsCache = getEffectsCache();

        log("Effects cache size: " + effectsCache.size());

        for (Object key: effectsCache.keySet()) {
            Object value = effectsCache.get(key);

            log("Effects cache: " + key + " = " + value);
        }

        loadConfig(effectsCache);

    }

    public void log(String message) {
        if (getConfig().getBoolean("verbose")) {
            logger.info(message);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadConfig(HashMap effectsCache) {

        // Bukkit MobEffectList = MCP Potion 
        // Bukkit MobEffect = MCP PotionEffect
        // TODO: call for customized effects
        //new net.minecraft.server.MobEffect

        // TODO: new effects (add custom to byId[32])
        HashMap<String,Integer> mobEffectNames = getMobEffectNames();


        List<Map<?,?>> maps = getConfig().getMapList("potions");
        for (Map<?,?> map: maps) {
            // Get potion name and damage value(s)
            List<Integer> damageValues;

            if (map.get("potion") instanceof Integer) {
                damageValues = new ArrayList<Integer>();
                damageValues.add((Integer)map.get("potion"));
            } else {
                String name = (String)map.get("potion");
                damageValues = getConfig().getIntegerList("damageValues."+name);
                if (damageValues == null) {
                    logger.warning("Invalid potion name "+name+", no damage values found in config");
                    continue;
                }
            }

            // TODO: configurable splash // if (map.get("splash") != null && ((Boolean)map.get("splash")).booleanValue()) {
                for (int dv: new ArrayList<Integer>(damageValues)) {
                    damageValues.add(dv + 16384);
                }
            //}

            // Build native effects list from config
            ArrayList<net.minecraft.server.v1_6_R2.MobEffect> nmsEffectsList = new ArrayList<net.minecraft.server.v1_6_R2.MobEffect>();

            List<List<Object>> effectsList = (List<List<Object>>)map.get("effects");

            if (effectsList == null) {
                log("No effects listed for "+map.get("potion")+", ignoring");
                continue;
            }

            for (List effectLine: effectsList) {
                String effectName = (String)effectLine.get(0);
                int amplification = ((Integer)effectLine.get(1)).intValue();
                int duration = ((Integer)effectLine.get(2)).intValue();

                int effectId = mobEffectNames.get(effectName);

                // Apply effects
                nmsEffectsList.add(new net.minecraft.server.v1_6_R2.MobEffect(effectId, duration, amplification));
            }

            // Apply all effects to all damage values
            log("Potion " + map.get("potion"));

            for (int damageValue: damageValues) {
                effectsCache.put(damageValue, nmsEffectsList);
                log("- Adding "+damageValue+" = "+nmsEffectsList);
            }
        }
    }

    /** Get map of internal mob effect names (potion.digSpeed) to ids (3) */
    HashMap<String,Integer> getMobEffectNames() {
        HashMap<String,Integer> map = new HashMap<String,Integer>();

        for (int i = 0; i < net.minecraft.server.v1_6_R2.MobEffectList.byId.length; i += 1) {
            if (net.minecraft.server.v1_6_R2.MobEffectList.byId[i] != null) {
                String name = net.minecraft.server.v1_6_R2.MobEffectList.byId[i].a(); // returns I; = MCP getName() was originally c(); could be a or b but only testing will tell.

                map.put(name, i);
                //map.put(name.replace("potion.", ""), i);
            }
        }

        return map;
    }

    /** Get localization strings (potion.prefix.*) for potions.

    Note: the indexes do NOT correspond to damage values
    And not all are used.. or even unused! (there is Clear Potion but no Milky Potion anywhere I can see)

    private static final String potionPrefixes[] =
    {
        "potion.prefix.mundane", "potion.prefix.uninteresting", "potion.prefix.bland", "potion.prefix.clear", "potion.prefix.milky", "potion.prefix.diffuse", "potion.prefix.artless", "potion.prefix.thin", "potion.prefix.awkward", "potion.prefix.flat",
        "potion.prefix.bulky", "potion.prefix.bungling", "potion.prefix.buttered", "potion.prefix.smooth", "potion.prefix.suave", "potion.prefix.debonair", "potion.prefix.thick", "potion.prefix.elegant", "potion.prefix.fancy", "potion.prefix.charming",
        "potion.prefix.dashing", "potion.prefix.refined", "potion.prefix.cordial", "potion.prefix.sparkling", "potion.prefix.potent", "potion.prefix.foul", "potion.prefix.odorless", "potion.prefix.rank", "potion.prefix.harsh", "potion.prefix.acrid",
        "potion.prefix.gross", "potion.prefix.stinky"
    };
    */

    /* unused in our code, too!
    String[] getAppearances() {
        try {
            Field field = net.minecraft.server.PotionBrewer.class.getDeclaredField("appearances"); // MCP potionPrefixes
            field.setAccessible(true);
            Object obj = field.get(null);
            if (obj instanceof String[]) {
                return (String[])obj;
            } else {
                throw new RuntimeException("Unable to access potion appearances, unexpected type: " + obj);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }   
    */

    /** Get the internal map of potion effects. 
    
    ItemPotion has a MCP 'private HashMap effectsCache', Bukkit 'private HashMap a()', public List b(int i)
    builds from MCP PotionHelper.getPotionEffects() = Bukkit PotionBrewer
    */
    HashMap getEffectsCache() {
        try {
            Field effectsCacheField = net.minecraft.server.v1_6_R2.ItemPotion.class.getDeclaredField("a");
            effectsCacheField.setAccessible(true);

            Object obj = effectsCacheField.get(net.minecraft.server.v1_6_R2.Item.POTION);
            if (obj instanceof HashMap) {
                return (HashMap)obj;
            } else {
                throw new RuntimeException("Unable to access effects cache, unexpected type: " + obj);
            }
        } catch (Exception e) {
            logger.severe("Reflection failed, nothing will work: " + e);
            throw new RuntimeException(e);
            // TODO: disable plugin
        }
    }

    public void onDisable() {
    }

}

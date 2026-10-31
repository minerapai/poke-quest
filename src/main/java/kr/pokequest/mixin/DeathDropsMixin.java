package kr.pokequest.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import kr.pokequest.Rival;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rival pokemon have no owner, so Cobblemon would drop their loot like a wild pokemon's when they faint: skip it. */
@Mixin(value = PokemonServerDelegate.class, remap = false)
public abstract class DeathDropsMixin {
    @Inject(method = "doDeathDrops", at = @At("HEAD"), cancellable = true)
    private void pokequest$noDrops(CallbackInfo ci) {
        if (((PokemonServerDelegate) (Object) this).getEntity().getPokemon().getPersistentData().getBoolean(Rival.TAG)) {
            ci.cancel();
        }
    }
}

package net.ciyuan.lynxtools.verification.mixin

import net.ciyuan.lynxtools.Runtime
import net.ciyuan.lynxtools.verification.ProbeState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Test-JAR-only access: production code exposes no testing entry point. */
@Mixin(value=[Runtime::class],remap=false)
abstract class RuntimeCapture {
    @Inject(method=["<init>"],at=[At("RETURN")],remap=false)
    private fun capture(ci: CallbackInfo) { ProbeState.app=(this as Any) as Runtime }
}

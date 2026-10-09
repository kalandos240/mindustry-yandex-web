package mindustry.web;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * The upstream JZlib jar provides another com.jcraft.jzlib.Adler32 on TeaVM's
 * classpath. An identically named web-runtime source class does not override
 * that dependency. Use TeaVM 0.15's explicit substitution API instead.
 */
@Autoregistered
public final class WebAdler32SubstitutionPolicy extends SimpleSubstitutionPolicy{
    @Override
    public void contribute(SubstitutionSink sink){
        sink.selectClasses(named("com.jcraft.jzlib.Adler32")).simpleNamePrefix("Web");
    }
}

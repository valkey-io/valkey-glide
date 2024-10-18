package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import java.util.ArrayList;

import glide.api.models.GlideString;
import lombok.Builder;

@Builder
public class FTExplainOptions {

    private final Double dialect;

        public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();

        if (dialect != null) {
            args.add(gs(dialect.toString()));
        }
        return args.toArray(GlideString[]::new);
    }
    
}

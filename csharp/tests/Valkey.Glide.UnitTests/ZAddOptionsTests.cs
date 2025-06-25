// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.UnitTests;

public class ZAddOptionsTests
{
    [Fact]
    public void ZAddOptions_SetConditionalChange_OnlyIfExists()
    {
        var options = new ZAddOptions().SetConditionalChange(ConditionalSet.OnlyIfExists);
        var args = options.ToArgs();
        
        Assert.Single(args);
        Assert.Equal("XX", args[0].ToString());
    }

    [Fact]
    public void ZAddOptions_SetConditionalChange_OnlyIfDoesNotExist()
    {
        var options = new ZAddOptions().SetConditionalChange(ConditionalSet.OnlyIfDoesNotExist);
        var args = options.ToArgs();
        
        Assert.Single(args);
        Assert.Equal("NX", args[0].ToString());
    }

    [Fact]
    public void ZAddOptions_SetUpdateOption_ScoreLessThanCurrent()
    {
        var options = new ZAddOptions().SetUpdateOption(UpdateOptions.ScoreLessThanCurrent);
        var args = options.ToArgs();
        
        Assert.Single(args);
        Assert.Equal("LT", args[0].ToString());
    }

    [Fact]
    public void ZAddOptions_SetUpdateOption_ScoreGreaterThanCurrent()
    {
        var options = new ZAddOptions().SetUpdateOption(UpdateOptions.ScoreGreaterThanCurrent);
        var args = options.ToArgs();
        
        Assert.Single(args);
        Assert.Equal("GT", args[0].ToString());
    }

    [Fact]
    public void ZAddOptions_SetChanged()
    {
        var options = new ZAddOptions().SetChanged(true);
        var args = options.ToArgs();
        
        Assert.Single(args);
        Assert.Equal("CH", args[0].ToString());
    }

    [Fact]
    public void ZAddOptions_CombinedOptions()
    {
        var options = new ZAddOptions()
            .SetConditionalChange(ConditionalSet.OnlyIfExists)
            .SetUpdateOption(UpdateOptions.ScoreGreaterThanCurrent)
            .SetChanged(true);
        var args = options.ToArgs();
        
        Assert.Equal(3, args.Count);
        Assert.True(args.Any(arg => arg.ToString() == "XX"));
        Assert.True(args.Any(arg => arg.ToString() == "GT"));
        Assert.True(args.Any(arg => arg.ToString() == "CH"));
    }

    [Fact]
    public void ZAddOptions_EmptyOptions()
    {
        var options = new ZAddOptions();
        var args = options.ToArgs();
        
        Assert.Empty(args);
    }
}

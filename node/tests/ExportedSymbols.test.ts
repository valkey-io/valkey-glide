import { it } from "@jest/globals";

var exportedSymbols = require('../index');
var exportedSymbolsRust = require('../rust-client/index')


describe("Exported Symbols test", () => {
    var excludedSymbols: string[] = [];
    beforeAll(() => {
        /**
         * Add Excluded symbols
         * Example:
         * excludedSymbols.push('convertGlideRecord');
         */

        /**
         * Add Excluded symbols for rust client
         * Example:
         * excludedSymbols.push('convertGlideRecord');
         */

    });
    it("check excluded symbols are not exported", () => {
        // Check exported symbols for valkey glide package
        let exportedSymbolsList = Object.keys(exportedSymbols);
        const filteredExportedList = exportedSymbolsList.filter(symbol => excludedSymbols.includes(symbol));
        console.log("Following symbols are exported but are in the exluded list, please remove: " + filteredExportedList);
        expect(filteredExportedList.length).toBe(0);

        // Check exported symbols from rust client package.
        let exportedRustSymbolsList = Object.keys(exportedSymbolsRust);
        const filteredExportedListForRust = exportedRustSymbolsList.filter(symbol => excludedSymbols.includes(symbol));
        console.log("Following symbols are exported but are in the exluded list, please remove: " + filteredExportedListForRust);
        expect(filteredExportedListForRust.length).toBe(0);
    });
});

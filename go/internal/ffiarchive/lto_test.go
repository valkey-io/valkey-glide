// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package ffiarchive holds a regression test for the prebuilt FFI archive that go/Makefile
// places in go/rustbin/<target>/libglide_ffi.a.
//
// Go 1.26.11 / 1.27.4 made cmd/go scan every directory named by -L in a
// package's #cgo LDFLAGS and reject any object or archive carrying LLVM/GCC LTO bitcode, which
// breaks `go build`, `go vet` and linters for every user of the module
// (https://github.com/valkey-io/valkey-glide/issues/7648). The bitcode comes from the prebuilt
// compiler_builtins objects that rustc copies verbatim into the archive, and go/Makefile removes
// it after the build.
//
// This test applies the same section rule as cmd/go (isDisallowedLTOSection in
// cmd/go/internal/work/security.go) using only the standard library, so it fails on any Go
// version, including the older ones CI uses, instead of only on a toolchain that has the check.
package ffiarchive

import (
	"bytes"
	"debug/elf"
	"debug/macho"
	"encoding/binary"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"testing"
)

// isDisallowedLTOSection mirrors cmd/go's rule for ELF sections and Mach-O section names.
func isDisallowedLTOSection(name string) bool {
	return strings.HasPrefix(name, ".gnu.lto_") ||
		strings.HasPrefix(name, ".gnu.debuglto_") ||
		strings.HasPrefix(name, ".llvm.lto") ||
		name == ".llvmbc" ||
		name == ".llvmcmd"
}

func isLTOSegment(name string) bool { return name == "__LLVM" || name == "__GNU_LTO" }

// ELF section types that cmd/go rejects whatever the section is called.
const (
	shtLLVMLinkerOptions      = 0x6fff4c01 // SHT_LLVM_LINKER_OPTIONS
	shtLLVMDependentLibraries = 0x6fff4c04 // SHT_LLVM_DEPENDENT_LIBRARIES
	shtLLVMLTO                = 0x6fff4c0c // SHT_LLVM_LTO
)

// ltoSections returns the sections of an ELF or Mach-O object that cmd/go rejects, nil for
// anything else (symbol tables, ...). Like cmd/go, it reports an error when the magic number
// says ELF or Mach-O but the object does not parse, so a corrupt member cannot pass silently.
func ltoSections(object []byte) ([]string, error) {
	if len(object) < 4 {
		return nil, nil
	}
	var hits []string
	if bytes.HasPrefix(object, []byte(elf.ELFMAG)) {
		f, err := elf.NewFile(bytes.NewReader(object))
		if err != nil {
			return nil, fmt.Errorf("invalid ELF object: %w", err)
		}
		defer f.Close()
		if f.Type == elf.ET_REL && len(f.Sections) == 0 {
			return nil, fmt.Errorf("ELF relocatable object missing section headers")
		}
		for _, s := range f.Sections {
			switch {
			case s.Type == shtLLVMLinkerOptions || s.Type == shtLLVMDependentLibraries || s.Type == shtLLVMLTO:
				hits = append(hits, fmt.Sprintf("%s (type %#x)", s.Name, uint32(s.Type)))
			case isDisallowedLTOSection(s.Name):
				hits = append(hits, s.Name)
			}
		}
		return hits, nil
	}
	if magic := binary.LittleEndian.Uint32(object); magic == macho.Magic32 || magic == macho.Magic64 ||
		binary.BigEndian.Uint32(object) == macho.Magic32 || binary.BigEndian.Uint32(object) == macho.Magic64 {
		f, err := macho.NewFile(bytes.NewReader(object))
		if err != nil {
			return nil, fmt.Errorf("invalid Mach-O object: %w", err)
		}
		defer f.Close()
		for _, l := range f.Loads {
			if seg, ok := l.(*macho.Segment); ok && isLTOSegment(seg.Name) {
				hits = append(hits, "segment "+seg.Name)
			}
		}
		for _, s := range f.Sections {
			if isLTOSegment(s.Seg) || isDisallowedLTOSection(s.Name) {
				hits = append(hits, s.Seg+","+s.Name)
			}
		}
	}
	return hits, nil
}

type arMember struct {
	name string
	data []byte
}

// arMembers lists the members of a GNU or BSD ar archive, without the symbol tables.
func arMembers(archive []byte) ([]arMember, error) {
	const magic = "!<arch>\n"
	if !bytes.HasPrefix(archive, []byte(magic)) {
		return nil, fmt.Errorf("not an ar archive")
	}
	var longNames []byte
	var members []arMember
	off := len(magic)
	for off+60 <= len(archive) {
		hdr := archive[off : off+60]
		size, err := strconv.Atoi(strings.TrimSpace(string(hdr[48:58])))
		if err != nil || size < 0 {
			return nil, fmt.Errorf("bad member size at offset %d", off)
		}
		start, end := off+60, off+60+size
		if end > len(archive) {
			return nil, fmt.Errorf("truncated member at offset %d", off)
		}
		raw, body := strings.TrimRight(string(hdr[0:16]), " "), archive[start:end]
		switch {
		case raw == "//":
			longNames = body
		case raw == "/" || raw == "/SYM64/":
			// GNU symbol table
		case strings.HasPrefix(raw, "#1/"): // BSD: the name is stored at the start of the data
			n, err := strconv.Atoi(raw[3:])
			if err != nil || n < 0 || n > len(body) {
				return nil, fmt.Errorf("bad BSD member name at offset %d", off)
			}
			name := strings.TrimRight(string(body[:n]), "\x00")
			if name != "__.SYMDEF" && name != "__.SYMDEF SORTED" {
				members = append(members, arMember{name, body[n:]})
			}
		case len(raw) > 1 && raw[0] == '/' && raw[1] >= '0' && raw[1] <= '9': // GNU long name
			idx, err := strconv.Atoi(raw[1:])
			if err != nil || idx < 0 || idx >= len(longNames) {
				return nil, fmt.Errorf("bad GNU long name reference at offset %d", off)
			}
			name := string(longNames[idx:])
			if i := strings.Index(name, "/\n"); i >= 0 {
				name = name[:i]
			}
			members = append(members, arMember{name, body})
		default:
			members = append(members, arMember{strings.TrimSuffix(raw, "/"), body})
		}
		off = end + size%2 // members are padded to an even offset
	}
	return members, nil
}

// TestFFIArchiveHasNoLTOBitcode checks every archive that `make build` put in go/rustbin.
func TestFFIArchiveHasNoLTOBitcode(t *testing.T) {
	archives, err := filepath.Glob(filepath.Join("..", "..", "rustbin", "*", "libglide_ffi.a"))
	if err != nil {
		t.Fatal(err)
	}
	if len(archives) == 0 {
		const msg = "no go/rustbin/<target>/libglide_ffi.a found; run `make build` first"
		if os.Getenv("GITHUB_ACTIONS") != "" {
			t.Fatal(msg) // CI must not pass without checking anything
		}
		t.Skip(msg)
	}
	for _, path := range archives {
		t.Run(filepath.Base(filepath.Dir(path)), func(t *testing.T) {
			data, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			members, err := arMembers(data)
			if err != nil {
				t.Fatalf("%s: %v", path, err)
			}
			var dirty []string
			for _, m := range members {
				hits, err := ltoSections(m.data)
				if err != nil {
					dirty = append(dirty, fmt.Sprintf("%s: %v", m.name, err))
				} else if len(hits) > 0 {
					sort.Strings(hits)
					dirty = append(dirty, fmt.Sprintf("%s %v", m.name, hits))
				}
			}
			if len(dirty) == 0 {
				return
			}
			if len(dirty) > 5 {
				dirty = append(dirty[:5], fmt.Sprintf("... and %d more", len(dirty)-5))
			}
			t.Errorf(
				"%s has LTO bitcode or embedded linker sections that Go >= 1.26.11 / 1.27.4 rejects (valkey-glide issue 7648); "+
					"go/Makefile's libglide_ffi target must strip it:\n  %s",
				path,
				strings.Join(dirty, "\n  "),
			)
		})
	}
}

// --- self-tests of the scanner, so it does not only depend on the archives being checked ---

func TestIsDisallowedLTOSection(t *testing.T) {
	for name, want := range map[string]bool{
		".llvmbc": true, ".llvmcmd": true, ".gnu.lto_foo": true, ".gnu.debuglto_foo": true,
		".llvm.lto": true, ".llvm.lto.x": true,
		".text": false, ".llvmbcx": false, ".llvm_addrsig": false, "__bitcode": false, "": false,
	} {
		if got := isDisallowedLTOSection(name); got != want {
			t.Errorf("isDisallowedLTOSection(%q) = %v, want %v", name, got, want)
		}
	}
}

type fakeSection struct {
	name string
	typ  elf.SectionType
}

func progbits(names ...string) []fakeSection {
	out := make([]fakeSection, len(names))
	for i, n := range names {
		out[i] = fakeSection{n, elf.SHT_PROGBITS}
	}
	return out
}

// fakeELF builds a minimal ELF64 relocatable object with the given (empty) sections.
func fakeELF(sections ...fakeSection) []byte {
	strtab := []byte{0}
	offsets := make([]uint32, len(sections))
	for i, s := range sections {
		offsets[i] = uint32(len(strtab))
		strtab = append(append(strtab, s.name...), 0)
	}
	shstrtabName := uint32(len(strtab))
	strtab = append(append(strtab, ".shstrtab"...), 0)

	le := binary.LittleEndian
	hdr := make([]byte, 64)
	copy(hdr, []byte{0x7f, 'E', 'L', 'F', 2, 1, 1})
	le.PutUint16(hdr[16:], uint16(elf.ET_REL))
	le.PutUint16(hdr[18:], uint16(elf.EM_X86_64))
	le.PutUint32(hdr[20:], 1)
	shoff := uint64(len(hdr) + len(strtab))
	le.PutUint64(hdr[40:], shoff)
	le.PutUint16(hdr[52:], 64) // ehsize
	le.PutUint16(hdr[58:], 64) // shentsize
	shnum := uint16(len(sections) + 2)
	le.PutUint16(hdr[60:], shnum)
	le.PutUint16(hdr[62:], shnum-1) // .shstrtab is the last section

	section := func(name uint32, typ elf.SectionType, off, size uint64) []byte {
		sh := make([]byte, 64)
		le.PutUint32(sh[0:], name)
		le.PutUint32(sh[4:], uint32(typ))
		le.PutUint64(sh[24:], off)
		le.PutUint64(sh[32:], size)
		return sh
	}
	out := append(hdr, strtab...)
	out = append(out, make([]byte, 64)...) // SHN_UNDEF
	for i, off := range offsets {
		out = append(out, section(off, sections[i].typ, uint64(len(hdr)), 0)...)
	}
	return append(out, section(shstrtabName, elf.SHT_STRTAB, uint64(len(hdr)), uint64(len(strtab)))...)
}

// fakeMachO builds a minimal 64-bit arm64 Mach-O object with one segment holding (segment, section) pairs.
func fakeMachO(segName string, sections ...[2]string) []byte {
	le := binary.LittleEndian
	cmdSize := 72 + 80*len(sections)
	out := make([]byte, 32+cmdSize)
	le.PutUint32(out[0:], 0xfeedfacf)
	le.PutUint32(out[4:], 0x0100000c) // CPU_TYPE_ARM64
	le.PutUint32(out[12:], 1)         // MH_OBJECT
	le.PutUint32(out[16:], 1)         // ncmds
	le.PutUint32(out[20:], uint32(cmdSize))
	cmd := out[32:]
	le.PutUint32(cmd[0:], 0x19) // LC_SEGMENT_64
	le.PutUint32(cmd[4:], uint32(cmdSize))
	copy(cmd[8:24], segName)
	le.PutUint32(cmd[64:], uint32(len(sections)))
	for i, s := range sections {
		sec := cmd[72+80*i:]
		copy(sec[0:16], s[1])
		copy(sec[16:32], s[0])
	}
	return out
}

func TestLTOSectionsDetectsBitcode(t *testing.T) {
	truncatedELF := append([]byte(elf.ELFMAG), 2, 1, 1, 0, 0, 0)
	truncatedMachO := []byte{0xcf, 0xfa, 0xed, 0xfe, 0x0c, 0x00}
	for name, tc := range map[string]struct {
		object  []byte
		want    []string
		wantErr bool
	}{
		"elf with bitcode":   {fakeELF(progbits(".text", ".llvmbc", ".llvmcmd")...), []string{".llvmbc", ".llvmcmd"}, false},
		"elf gcc lto":        {fakeELF(progbits(".gnu.lto_foo.1")...), []string{".gnu.lto_foo.1"}, false},
		"elf clean":          {fakeELF(progbits(".text", ".rodata", ".eh_frame")...), nil, false},
		"elf LLVM_LTO type":  {fakeELF(fakeSection{".renamed", shtLLVMLTO}), []string{".renamed (type 0x6fff4c0c)"}, false},
		"elf linker options": {fakeELF(fakeSection{".linker-options", shtLLVMLinkerOptions}), []string{".linker-options (type 0x6fff4c01)"}, false},
		"elf dependent libs": {fakeELF(fakeSection{".deplibs", shtLLVMDependentLibraries}), []string{".deplibs (type 0x6fff4c04)"}, false},
		"elf truncated":      {truncatedELF, nil, true},
		"macho with bitcode": {fakeMachO("__LLVM", [2]string{"__LLVM", "__bitcode"}, [2]string{"__LLVM", "__cmdline"}), []string{"segment __LLVM", "__LLVM,__bitcode", "__LLVM,__cmdline"}, false},
		"macho clean":        {fakeMachO("__TEXT", [2]string{"__TEXT", "__text"}), nil, false},
		"macho truncated":    {truncatedMachO, nil, true},
		"not an object":      {[]byte("just some rmeta bytes"), nil, false},
	} {
		t.Run(name, func(t *testing.T) {
			got, err := ltoSections(tc.object)
			if (err != nil) != tc.wantErr {
				t.Fatalf("ltoSections() error = %v, wantErr %v", err, tc.wantErr)
			}
			if strings.Join(got, "|") != strings.Join(tc.want, "|") {
				t.Errorf("ltoSections() = %v, want %v", got, tc.want)
			}
		})
	}
}

func TestArMembers(t *testing.T) {
	member := func(name string, body string) string {
		hdr := fmt.Sprintf("%-16s%-12s%-6s%-6s%-8s%-10d`\n", name, "0", "0", "0", "100644", len(body))
		if len(body)%2 == 1 {
			body += "\n"
		}
		return hdr + body
	}
	longNames := "a_very_long_member_name.o/\n"
	gnu := "!<arch>\n" + member("/", "symtab") + member("//", longNames) +
		member("/0", "elf-one") + member("short.o/", "elf-two!")
	bsd := "!<arch>\n" + member("#1/20", "__.SYMDEF SORTED\x00\x00\x00\x00symbols") +
		member("#1/12", "long_name.o\x00object")

	for name, tc := range map[string]struct {
		archive string
		want    string
	}{
		"gnu": {gnu, "a_very_long_member_name.o=elf-one|short.o=elf-two!"},
		"bsd": {bsd, "long_name.o=object"},
	} {
		t.Run(name, func(t *testing.T) {
			members, err := arMembers([]byte(tc.archive))
			if err != nil {
				t.Fatal(err)
			}
			var got []string
			for _, m := range members {
				got = append(got, m.name+"="+string(m.data))
			}
			if strings.Join(got, "|") != tc.want {
				t.Errorf("arMembers() = %v, want %v", got, tc.want)
			}
		})
	}
	if _, err := arMembers([]byte("not an archive")); err == nil {
		t.Error("expected an error for a non-ar input")
	}
}

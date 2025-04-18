import * as fs from "fs";
import * as path from "path";

const docsDirectory = "../docs/markdown/node";

function convertLinksInFile(filePath) {
    const content = fs.readFileSync(filePath, "utf8");

    const regex =
        /\[(https:\/\/[^|]+)\|([^\]]+)\]\((https:\/\/[^|]+)\|([^)]+)\)/g;
    let updatedContent = content.replace(regex, "[$2]($1)");

    // Clean up stray backslashes before closing parens
    updatedContent = updatedContent.replace(/\\(?=\))/g, "");

    fs.writeFileSync(filePath, updatedContent);
}

function processDirectory(directory) {
    const files = fs.readdirSync(directory);
    files.forEach((file) => {
        // Sanitize the file path to prevent directory traversal
        const sanitizedFile = path
            .basename(file)
            .replace(/\.\./g, "")
            .replace(/\//g, "");
        // nosemgrep
        const fullPath = path.resolve(directory, sanitizedFile);

        // nosemgrep
        if (!fullPath.startsWith(path.resolve(directory))) {
            return;
        }

        const stat = fs.statSync(fullPath);

        if (stat.isDirectory()) {
            processDirectory(fullPath);
        } else if (path.extname(sanitizedFile) === ".md") {
            convertLinksInFile(fullPath);
        }
    });
}

processDirectory(docsDirectory);

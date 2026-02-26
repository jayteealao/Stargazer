---
name: dailytags
description: Comprehensive guide for using DailyTags - a Jetpack Compose markdown library that supports custom tags. Use this skill when working with markdown rendering, custom markup, rich text formatting in Compose, or creating pattern-based text parsers for Android applications. DailyTags parses text into nodes styled with AnnotatedString, providing a WebView-free solution for rendering markdown and HTML with full customization support.
---

# DailyTags Skill

## Overview

DailyTags is a flexible markdown library for Jetpack Compose that parses markup into rich text using AnnotatedString. It's lightweight (<50kb), fast, extensible, and requires no WebView.

**Key Capabilities:**
- ✅ Markdown parsing with built-in rules
- ✅ HTML support with built-in rules  
- ✅ Custom tag/markup creation via regex patterns
- ✅ Full styling control (SpanStyle, ParagraphStyle)
- ✅ Node-based parsing architecture
- ✅ Native Compose integration

**Important Limitation:**
- ⚠️ **Text content only** - Cannot render images, tables, checkboxes, or custom UI elements
- For non-text elements, use hybrid approach with regular Compose components

## Installation

### Gradle Setup

Add JitPack repository to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependency to your module `build.gradle`:

```gradle
dependencies {
    implementation "com.github.DmytroShuba:DailyTags:1.0.0"
}
```

**Current Version:** 1.0.0 (February 2022)  
**Min API Level:** 23+  
**License:** MIT

## Core Architecture

### Parsing Flow

```
Source Text → Rules → Parser → Nodes → Render → AnnotatedString → Text Composable
```

### Key Components

1. **SimpleMarkupParser**: Main parsing engine that processes text using rules
2. **Rules**: Pattern-based definitions for how to extract and style content
3. **Nodes**: Internal representation of parsed elements
4. **AnnotatedString**: Compose's rich text format (final output)

## Basic Usage

### 1. Simple Markdown Rendering

```kotlin
import com.dmytroshuba.dailytags.markdown.rules.MarkdownRules
import com.dmytroshuba.dailytags.core.simple.SimpleMarkupParser
import com.dmytroshuba.dailytags.core.simple.render
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MarkdownText(source: String) {
    val parser = SimpleMarkupParser()
    val rules = MarkdownRules.toList()
    
    val content = parser
        .parse(source, rules)
        .render()
        .toAnnotatedString()
    
    Text(text = content)
}

// Usage
MarkdownText("**Bold text** and *italic text*")
```

### 2. Markdown + HTML Combined

```kotlin
import com.dmytroshuba.dailytags.markdown.rules.HtmlRules

@Composable
fun RichText(source: String) {
    val parser = SimpleMarkupParser()
    val rules = MarkdownRules.toList() + HtmlRules.toList()
    
    val content = parser
        .parse(source, rules)
        .render()
        .toAnnotatedString()
    
    Text(text = content)
}

// Usage
RichText("""
    <b>HTML bold</b> and **Markdown bold**
    <i>HTML italic</i> and *Markdown italic*
""")
```

## Custom Tags & Markup

### Creating Custom Rules

#### Step 1: Define Pattern

Use Java's `Pattern` class to create regex patterns:

```kotlin
import java.util.regex.Pattern

// Highlight tag: <hl-blue>text</hl-blue>
val PATTERN_HIGHLIGHT_BLUE = Pattern.compile("^<hl-blue>([\\s\\S]+?)<\\/hl-blue>")

// Spoiler tag: ||hidden text||
val PATTERN_SPOILER = Pattern.compile("^\\|\\|([\\s\\S]+?)\\|\\|")

// Mention tag: @username
val PATTERN_MENTION = Pattern.compile("^@([a-zA-Z0-9_]+)")

// Code language tag: ```kotlin code ```
val PATTERN_CODE_BLOCK = Pattern.compile("^```(\\w+)\\n([\\s\\S]+?)```")
```

#### Step 2: Convert Pattern to Rule

Use the `toRule()` extension function:

```kotlin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.dmytroshuba.dailytags.core.simple.toRule

// Basic styling
val blueHighlightRule = PATTERN_HIGHLIGHT_BLUE.toRule(
    spanStyle = SpanStyle(
        color = Color.Blue,
        background = Color.Blue.copy(alpha = 0.1f)
    )
)

// Complex styling with multiple properties
val spoilerRule = PATTERN_SPOILER.toRule(
    spanStyle = SpanStyle(
        color = Color.Black,
        background = Color.DarkGray
    )
)

val mentionRule = PATTERN_MENTION.toRule(
    spanStyle = SpanStyle(
        color = Color(0xFF1DA1F2), // Twitter blue
        fontWeight = FontWeight.Bold
    )
)
```

#### Step 3: Use Custom Rules

```kotlin
@Composable
fun CustomMarkupText(source: String) {
    val parser = SimpleMarkupParser()
    
    // Combine built-in and custom rules
    val rules = MarkdownRules.toList() + listOf(
        blueHighlightRule,
        spoilerRule,
        mentionRule
    )
    
    val content = parser
        .parse(source, rules)
        .render()
        .toAnnotatedString()
    
    Text(text = content)
}

// Usage
CustomMarkupText("""
    Regular text with <hl-blue>blue highlight</hl-blue>
    This is a ||spoiler||
    Hey @username, check this out!
""")
```

## Advanced Patterns

### 1. Paragraph Styling

```kotlin
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextIndent

val PATTERN_QUOTE = Pattern.compile("^> (.+)$", Pattern.MULTILINE)

val quoteRule = PATTERN_QUOTE.toRule(
    spanStyle = SpanStyle(
        color = Color.Gray,
        fontStyle = FontStyle.Italic
    ),
    paragraphStyle = ParagraphStyle(
        textIndent = TextIndent(firstLine = 16.sp),
        lineHeight = 20.sp
    )
)
```

### 2. Multi-Group Patterns

Extract and style different groups separately:

```kotlin
// Pattern with capturing groups
val PATTERN_LINK = Pattern.compile("^\\[([^\\]]+)\\]\\(([^)]+)\\)")

// Custom rendering with different styles for text and URL
val linkRule = PATTERN_LINK.toRule(
    spanStyle = SpanStyle(
        color = Color.Blue,
        textDecoration = TextDecoration.Underline
    )
)
```

### 3. Nested Rules

Rules are processed in order, allowing nested markup:

```kotlin
val rules = listOf(
    // Process outer tags first
    blockquoteRule,
    // Then inner formatting
    boldRule,
    italicRule,
    codeRule
)
```

### 4. Conditional Styling

Create rules that apply different styles based on content:

```kotlin
val PATTERN_TAG = Pattern.compile("^#(\\w+)")

fun createTagRule(color: Color) = PATTERN_TAG.toRule(
    spanStyle = SpanStyle(
        color = color,
        fontWeight = FontWeight.Medium,
        background = color.copy(alpha = 0.1f)
    )
)

// Usage with different colors
val tagRules = listOf(
    createTagRule(Color.Red),
    createTagRule(Color.Blue),
    createTagRule(Color.Green)
)
```

## Important Limitations

**DailyTags works with TEXT CONTENT ONLY.** It cannot render:

- ❌ Images (including `![alt](url)` and `<img>` tags)
- ❌ Tables
- ❌ Checkboxes / Task lists
- ❌ Custom UI elements
- ❌ Embedded media (video, audio)
- ❌ Interactive widgets

**What this means:**
- Image syntax like `![Alt text](url)` will be parsed but NOT display an image
- Table markdown will be treated as plain text
- Checkbox syntax `- [ ]` will render as text, not interactive checkboxes
- Only text styling (bold, italic, color, etc.) is supported

**For these features, you need to:**
- Use regular Compose Image() composables separately
- Build custom UI components outside DailyTags
- Consider hybrid approaches (DailyTags for text, Compose for UI elements)

## Supported Markdown Features

### Text Formatting

- **Bold**: `**text**` or `__text__`
- *Italic*: `*text*` or `_text_`
- ~~Strikethrough~~: `~~text~~`
- `Inline code`: `` `code` ``
- Combined: `***bold italic***`

### Headings

```markdown
# H1 Heading
## H2 Heading
### H3 Heading
#### H4 Heading
##### H5 Heading
###### H6 Heading
```

### Lists

```markdown
- Unordered list item 1
- Unordered list item 2
  - Nested item

1. Ordered list item 1
2. Ordered list item 2
   1. Nested numbered item
```

### Links and Images (Text Only)

**Links:**
```markdown
[Link text](https://example.com)
```
Note: Links are styled but not clickable by default. Use ClickableText for interactivity.

**Images:**
```markdown
![Alt text](https://example.com/image.jpg)
```
⚠️ **Important:** Image markdown is parsed as text only - no actual image rendering. You'll see the alt text, not the image. Use Compose's `Image()` composable separately if you need to display images.

### Code Blocks

````markdown
```
Code block
```

```kotlin
fun example() {
    println("Language-specific")
}
```
````

### Blockquotes

```markdown
> This is a quote
> It can span multiple lines
```

### Horizontal Rules

```markdown
---
***
___
```

## Supported HTML Tags

When using `HtmlRules`:

**Text Formatting:**
- `<b>` / `<strong>` - Bold
- `<i>` / `<em>` - Italic
- `<u>` - Underline
- `<s>` / `<strike>` - Strikethrough
- `<code>` - Inline code
- `<pre>` - Preformatted text

**Structure:**
- `<h1>` to `<h6>` - Headings
- `<p>` - Paragraphs
- `<br>` / `<br/>` - Line breaks
- `<ul>`, `<ol>`, `<li>` - Lists
- `<blockquote>` - Quotes

**Links (Text Only):**
- `<a href="">` - Links (styled but not clickable by default)

**Not Rendered:**
- `<img src="">` - ⚠️ Parsed but does NOT display images
- `<table>`, `<tr>`, `<td>` - ⚠️ Not supported, treated as plain text
- `<video>`, `<audio>` - ⚠️ Not supported
- `<input>`, `<button>` - ⚠️ Not supported

## Best Practices

### 1. Hybrid Approach for Images and UI Elements

Since DailyTags only handles text, combine it with regular Compose for non-text elements:

```kotlin
@Composable
fun RichContent(markdown: String, images: List<ImageData>) {
    Column {
        // Render markdown text
        val textContent = parseMarkdown(markdown)
        Text(text = textContent)
        
        // Render images separately
        images.forEach { imageData ->
            AsyncImage(
                model = imageData.url,
                contentDescription = imageData.alt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}
```

**For tables, use custom Compose layouts:**
```kotlin
@Composable
fun MarkdownWithTable(
    beforeTable: String,
    tableData: TableData,
    afterTable: String
) {
    Column {
        MarkdownText(beforeTable)
        
        // Custom table rendering
        TableComposable(tableData)
        
        MarkdownText(afterTable)
    }
}
```

**For checkboxes/task lists:**
```kotlin
@Composable
fun TaskList(tasks: List<Task>) {
    tasks.forEach { task ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { task.onToggle() }
            )
            // Use DailyTags for task description
            MarkdownText(task.description)
        }
    }
}
```

### 2. Rule Organization

```kotlin
object CustomMarkupRules {
    // Define all patterns
    private val PATTERN_HIGHLIGHT = Pattern.compile("...")
    private val PATTERN_SPOILER = Pattern.compile("...")
    private val PATTERN_MENTION = Pattern.compile("...")
    
    // Define all rules
    private val highlightRule = PATTERN_HIGHLIGHT.toRule(...)
    private val spoilerRule = PATTERN_SPOILER.toRule(...)
    private val mentionRule = PATTERN_MENTION.toRule(...)
    
    // Public function to get all rules
    fun toList() = listOf(
        highlightRule,
        spoilerRule,
        mentionRule
    )
}

// Usage
val rules = MarkdownRules.toList() + CustomMarkupRules.toList()
```

### 2. Parser Reuse

Cache the parser instance for better performance:

```kotlin
class MarkdownViewModel : ViewModel() {
    private val parser = SimpleMarkupParser()
    private val rules = MarkdownRules.toList()
    
    fun parseMarkdown(source: String): AnnotatedString {
        return parser
            .parse(source, rules)
            .render()
            .toAnnotatedString()
    }
}
```

### 3. Theme Integration

Adapt styling to Material Theme:

```kotlin
@Composable
fun ThemedMarkdown(source: String) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    val customRules = remember(colorScheme, typography) {
        listOf(
            createCodeRule(colorScheme.primaryContainer),
            createLinkRule(colorScheme.primary),
            createQuoteRule(colorScheme.outline)
        )
    }
    
    val allRules = MarkdownRules.toList() + customRules
    // ... parse and render
}
```

### 4. Performance Optimization

For long documents, consider lazy rendering:

```kotlin
@Composable
fun LongMarkdownDocument(source: String) {
    val sections = remember(source) {
        source.split("\n\n") // Split by paragraphs
    }
    
    LazyColumn {
        items(sections) { section ->
            MarkdownText(section)
        }
    }
}
```

### 5. Interactive Elements

Combine with clickable modifiers:

```kotlin
val PATTERN_CLICKABLE = Pattern.compile("^\\[\\[([^\\]]+)\\]\\]")

@Composable
fun InteractiveMarkdown(
    source: String,
    onLinkClick: (String) -> Unit
) {
    val annotatedString = parser
        .parse(source, rules)
        .render()
        .toAnnotatedString()
    
    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            // Handle clicks on links
            annotatedString.getStringAnnotations(
                tag = "URL",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                onLinkClick(annotation.item)
            }
        }
    )
}
```

## Common Patterns & Examples

### 1. Chat Message Formatting

```kotlin
val PATTERN_USER_MENTION = Pattern.compile("^@([a-zA-Z0-9_]+)")
val PATTERN_CHANNEL = Pattern.compile("^#([a-zA-Z0-9_-]+)")
val PATTERN_EMOJI = Pattern.compile("^:([a-zA-Z0-9_]+):")

val chatRules = listOf(
    PATTERN_USER_MENTION.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF1DA1F2),
            fontWeight = FontWeight.Bold
        )
    ),
    PATTERN_CHANNEL.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF4A90E2),
            fontWeight = FontWeight.Medium
        )
    ),
    PATTERN_EMOJI.toRule(
        spanStyle = SpanStyle(fontSize = 20.sp)
    )
) + MarkdownRules.toList()
```

### 2. Code Documentation

```kotlin
val PATTERN_PARAM = Pattern.compile("^@param\\s+(\\w+)\\s+(.+)$", Pattern.MULTILINE)
val PATTERN_RETURN = Pattern.compile("^@return\\s+(.+)$", Pattern.MULTILINE)

val docRules = listOf(
    PATTERN_PARAM.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF569CD6),
            fontFamily = FontFamily.Monospace
        )
    ),
    PATTERN_RETURN.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF4EC9B0),
            fontFamily = FontFamily.Monospace
        )
    )
) + MarkdownRules.toList()
```

### 3. Syntax Highlighting Preview

```kotlin
val PATTERN_KEYWORD = Pattern.compile("^\\b(fun|val|var|class|object)\\b")
val PATTERN_STRING = Pattern.compile("^\"([^\"]*?)\"")
val PATTERN_COMMENT = Pattern.compile("^//(.+)$", Pattern.MULTILINE)

val syntaxRules = listOf(
    PATTERN_KEYWORD.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFFCC7832),
            fontWeight = FontWeight.Bold
        )
    ),
    PATTERN_STRING.toRule(
        spanStyle = SpanStyle(color = Color(0xFF6A8759))
    ),
    PATTERN_COMMENT.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF808080),
            fontStyle = FontStyle.Italic
        )
    )
)
```

### 4. Social Media Post

```kotlin
val PATTERN_HASHTAG = Pattern.compile("^#(\\w+)")
val PATTERN_URL = Pattern.compile("^(https?://[^\\s]+)")

val socialRules = listOf(
    PATTERN_HASHTAG.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF1DA1F2),
            fontWeight = FontWeight.Medium
        )
    ),
    PATTERN_URL.toRule(
        spanStyle = SpanStyle(
            color = Color(0xFF1DA1F2),
            textDecoration = TextDecoration.Underline
        )
    )
) + MarkdownRules.toList()
```

### 5. Math Notation (Simple)

```kotlin
val PATTERN_INLINE_MATH = Pattern.compile("^\\$([^\\$]+)\\$")
val PATTERN_BLOCK_MATH = Pattern.compile("^\\$\\$([\\s\\S]+?)\\$\\$")

val mathRules = listOf(
    PATTERN_INLINE_MATH.toRule(
        spanStyle = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF8B4513)
        )
    ),
    PATTERN_BLOCK_MATH.toRule(
        spanStyle = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp
        ),
        paragraphStyle = ParagraphStyle(
            textAlign = TextAlign.Center
        )
    )
)
```

## Troubleshooting

### Pattern Not Matching

**Problem:** Custom pattern doesn't match expected text

**Solutions:**

1. Test regex separately:
```kotlin
val pattern = Pattern.compile("your pattern here")
val matcher = pattern.matcher("test text")
if (matcher.find()) {
    println("Match: ${matcher.group()}")
} else {
    println("No match")
}
```

2. Use proper flags:
```kotlin
// Case insensitive
Pattern.compile("pattern", Pattern.CASE_INSENSITIVE)

// Multiline mode
Pattern.compile("pattern", Pattern.MULTILINE)

// Dot matches newlines
Pattern.compile("pattern", Pattern.DOTALL)

// Combine flags
Pattern.compile("pattern", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE)
```

3. Escape special characters:
```kotlin
// Wrong: Pattern.compile("^$")
// Correct:
Pattern.compile("^\\$")
```

### Rules Not Applied

**Problem:** Rules defined but styling not appearing

**Checklist:**

1. ✓ Rules added to parser: `parser.parse(source, rules)`
2. ✓ Rule order matters: More specific rules should come before general ones
3. ✓ Pattern starts with `^`: All patterns must start with `^` anchor
4. ✓ Called `.render().toAnnotatedString()`: Both methods required
5. ✓ SpanStyle vs ParagraphStyle: Use appropriate style type

### Performance Issues

**Problem:** Slow rendering for large documents

**Solutions:**

1. Split content into sections and render lazily:
```kotlin
LazyColumn {
    items(sections) { section ->
        MarkdownSection(section)
    }
}
```

2. Cache parsed results:
```kotlin
val cachedContent = remember(source) {
    parser.parse(source, rules)
        .render()
        .toAnnotatedString()
}
```

3. Limit rule complexity - simpler patterns parse faster

### Styling Not Visible

**Problem:** Styles defined but not visible in UI

**Debug steps:**

1. Check Text composable properties:
```kotlin
Text(
    text = content,
    // Ensure these don't override AnnotatedString styling
    color = Color.Unspecified, // Don't override
    fontSize = TextUnit.Unspecified // Don't override
)
```

2. Verify SpanStyle colors contrast with background
3. Check MaterialTheme isn't overriding colors

### Nested Tags Not Working

**Problem:** Tags inside tags not parsing correctly

**Solution:** Order rules from outermost to innermost:

```kotlin
val rules = listOf(
    blockquoteRule,      // Outer
    boldRule,            // Middle  
    italicRule,          // Middle
    inlineCodeRule       // Inner
)
```

## Migration & Version Notes

**From Plain Text:**
- Simply wrap existing text with DailyTags parser
- No changes needed to text format for basic markdown

**From WebView:**
- Remove WebView dependencies
- Replace with DailyTags parser
- Significant performance improvement
- Better integration with Compose UI

**From Other Markdown Libraries:**
- Check supported features list (not all markdown specs supported)
- Some features may need custom rules
- Pattern syntax uses Java regex

## Resources

- **GitHub Repository**: https://github.com/DmytroShuba/DailyTags
- **JitPack**: https://jitpack.io/#DmytroShuba/DailyTags
- **Wiki**: https://github.com/DmytroShuba/DailyTags/wiki
- **Issues**: https://github.com/DmytroShuba/DailyTags/issues

## Quick Reference

### Essential Imports

```kotlin
import com.dmytroshuba.dailytags.core.simple.SimpleMarkupParser
import com.dmytroshuba.dailytags.core.simple.render
import com.dmytroshuba.dailytags.core.simple.toRule
import com.dmytroshuba.dailytags.markdown.rules.MarkdownRules
import com.dmytroshuba.dailytags.markdown.rules.HtmlRules
import java.util.regex.Pattern
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
```

### Minimal Working Example

```kotlin
@Composable
fun MinimalMarkdown() {
    val source = "**Bold** and *italic* text"
    val parser = SimpleMarkupParser()
    val content = parser
        .parse(source, MarkdownRules.toList())
        .render()
        .toAnnotatedString()
    
    Text(text = content)
}
```

### Complete Custom Rule Example

```kotlin
// 1. Pattern
val PATTERN = Pattern.compile("^<tag>([\\s\\S]+?)</tag>")

// 2. Rule
val rule = PATTERN.toRule(
    spanStyle = SpanStyle(
        color = Color.Red,
        fontWeight = FontWeight.Bold
    )
)

// 3. Parse
val content = SimpleMarkupParser()
    .parse(source, listOf(rule))
    .render()
    .toAnnotatedString()

// 4. Display
Text(text = content)
```

## Remember

1. **Text content only** - No images, tables, checkboxes, or custom UI elements
2. **Patterns must start with `^`** - This is mandatory for DailyTags
3. **Rule order matters** - Process outer/general rules before inner/specific ones  
4. **Both render() and toAnnotatedString() required** - Don't skip either step
5. **Use remember() for performance** - Cache parsed results when possible
6. **Test patterns independently** - Verify regex before creating rules
7. **SpanStyle for inline, ParagraphStyle for blocks** - Choose the right style type
8. **Combine built-in rules freely** - MarkdownRules + HtmlRules + CustomRules
9. **Library is lightweight** - No WebView overhead, pure Compose
10. **Use hybrid approach** - Combine DailyTags text rendering with regular Compose UI for non-text elements

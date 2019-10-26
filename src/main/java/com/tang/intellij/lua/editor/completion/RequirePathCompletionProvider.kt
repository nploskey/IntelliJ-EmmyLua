/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.sanitizeFileName
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.project.LuaSourceRootManager

/**
 *
 * Created by tangzx on 2016/12/25.
 */
class RequirePathCompletionProvider : LuaCompletionProvider() {
    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet
        val file = completionParameters.originalFile
        val cur = file.findElementAt(completionParameters.offset - 1)
        if (cur != null) {
            val ls = LuaString.getContent(cur.text)
            val content = when (LuaSettings.instance.importPathSeparator) {
                "."  -> ls.value.replace('/', '.')
                else -> ls.value
            }
            val resultSet = completionResultSet.withPrefixMatcher(content)
            addAllFiles(completionParameters, resultSet)
        }

        completionResultSet.stopHere()
    }

    private fun addAllFiles(completionParameters: CompletionParameters, completionResultSet: CompletionResultSet) {
        val project = completionParameters.originalFile.project
        val sourceRoots = LuaSourceRootManager.getInstance(project).getSourceRoots()
        for (sourceRoot in sourceRoots) {
            addAllFiles(project, completionResultSet, null, sourceRoot.children)
        }
    }

    private fun addAllFiles(project: Project, completionResultSet: CompletionResultSet, importPath: String?, children: Array<VirtualFile>) {
        val sep = LuaSettings.instance.importPathSeparator

        for (child in children) {
            if (!LuaSourceRootManager.getInstance(project).isInSource(child))
                continue

            val isDir = child.isDirectory
            var fileName = if (isDir) child.name else FileUtil.getNameWithoutExtension(child.name)
            if (sep == ".") {
                // Insert warning indicators if the filename contains dots.
                fileName = fileName.replace('.', '!')
            }
            val newPath = if (importPath == null) fileName else "$importPath$sep$fileName"

            if (isDir) {
                addAllFiles(project, completionResultSet, newPath, child.children)
            } else if (child.fileType === LuaFileType.INSTANCE) {
                val lookupElement = LookupElementBuilder
                        .create(newPath)
                        .withIcon(LuaIcons.FILE)
                        .withInsertHandler(FullPackageInsertHandler())
                completionResultSet.addElement(PrioritizedLookupElement.withPriority(lookupElement, 1.0))
            }
        }
    }

    private fun newChildImportPath(importPath: String?, child: VirtualFile): String {
        val sep = LuaSettings.instance.importPathSeparator
        val fileName = when {
            child.isDirectory -> child.name
            else              -> FileUtil.getNameWithoutExtension(child.name)
        }
        return if (importPath == null) fileName else "$importPath$sep$fileName"
    }

    internal class FullPackageInsertHandler : InsertHandler<LookupElement> {

        override fun handleInsert(insertionContext: InsertionContext, lookupElement: LookupElement) {
            val tailOffset = insertionContext.tailOffset
            val cur = insertionContext.file.findElementAt(tailOffset)

            if (cur != null) {
                val start = cur.textOffset

                val ls = LuaString.getContent(cur.text)
                insertionContext.document.deleteString(start + ls.start, start + ls.end)

                val lookupString = lookupElement.lookupString
                insertionContext.document.insertString(start + ls.start, lookupString)
                insertionContext.editor.caretModel.moveToOffset(start + ls.start + lookupString.length)
            }
        }
    }

}

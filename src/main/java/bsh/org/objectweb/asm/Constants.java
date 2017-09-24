/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000,2002,2003 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Contact: Eric.Bruneton@rd.francetelecom.com
 *
 * Author: Eric Bruneton
 */
package bsh.org.objectweb.asm;

/**
 * Defines the JVM opcodes, access flags and array type codes. This interface
 * does not define all the JVM opcodes because some opcodes are automatically
 * handled. For example, the xLOAD and xSTORE opcodes are automatically replaced
 * by xLOAD_n and xSTORE_n opcodes when possible. The xLOAD_n and xSTORE_n
 * opcodes are therefore not defined in this interface. Likewise for LDC,
 * automatically replaced by LDC_W or LDC2_W when necessary, WIDE, GOTO_W and
 * JSR_W.
 */
public interface Constants {

    // access flags
    /** The acc public. */
    int ACC_PUBLIC = 1;
    /** The acc private. */
    int ACC_PRIVATE = 2;
    /** The acc protected. */
    int ACC_PROTECTED = 4;
    /** The acc static. */
    int ACC_STATIC = 8;
    /** The acc final. */
    int ACC_FINAL = 16;
    /** The acc synchronized. */
    int ACC_SYNCHRONIZED = 32;
    /** The acc volatile. */
    int ACC_VOLATILE = 64;
    /** The acc transient. */
    int ACC_TRANSIENT = 128;
    /** The acc native. */
    int ACC_NATIVE = 256;
    /** The acc interface. */
    int ACC_INTERFACE = 512;
    /** The acc abstract. */
    int ACC_ABSTRACT = 1024;
    /** The acc strict. */
    int ACC_STRICT = 2048;
    /** The acc super. */
    int ACC_SUPER = 32;
    /** The acc synthetic. */
    int ACC_SYNTHETIC = 65536;
    /** The acc deprecated. */
    int ACC_DEPRECATED = 131072;
    // types for NEWARRAY
    /** The t boolean. */
    int T_BOOLEAN = 4;
    /** The t char. */
    int T_CHAR = 5;
    /** The t float. */
    int T_FLOAT = 6;
    /** The t double. */
    int T_DOUBLE = 7;
    /** The t byte. */
    int T_BYTE = 8;
    /** The t short. */
    int T_SHORT = 9;
    /** The t int. */
    int T_INT = 10;
    /** The t long. */
    int T_LONG = 11;
    // opcodes // visit method (- = idem)
    /** The nop. */
    int NOP = 0; // visitInsn
    /** The aconst null. */
    int ACONST_NULL = 1; // -
    /** The iconst m1. */
    int ICONST_M1 = 2; // -
    /** The iconst 0. */
    int ICONST_0 = 3; // -
    /** The iconst 1. */
    int ICONST_1 = 4; // -
    /** The iconst 2. */
    int ICONST_2 = 5; // -
    /** The iconst 3. */
    int ICONST_3 = 6; // -
    /** The iconst 4. */
    int ICONST_4 = 7; // -
    /** The iconst 5. */
    int ICONST_5 = 8; // -
    /** The lconst 0. */
    int LCONST_0 = 9; // -
    /** The lconst 1. */
    int LCONST_1 = 10; // -
    /** The fconst 0. */
    int FCONST_0 = 11; // -
    /** The fconst 1. */
    int FCONST_1 = 12; // -
    /** The fconst 2. */
    int FCONST_2 = 13; // -
    /** The dconst 0. */
    int DCONST_0 = 14; // -
    /** The dconst 1. */
    int DCONST_1 = 15; // -
    /** The bipush. */
    int BIPUSH = 16; // visitIntInsn
    /** The sipush. */
    int SIPUSH = 17; // -
    /** The ldc. */
    int LDC = 18; // visitLdcInsn
    // int LDC_W = 19; // -
    // int LDC2_W = 20; // -
    /** The iload. */
    int ILOAD = 21; // visitVarInsn
    /** The lload. */
    int LLOAD = 22; // -
    /** The fload. */
    int FLOAD = 23; // -
    /** The dload. */
    int DLOAD = 24; // -
    /** The aload. */
    int ALOAD = 25; // -
    // int ILOAD_0 = 26; // -
    // int ILOAD_1 = 27; // -
    // int ILOAD_2 = 28; // -
    // int ILOAD_3 = 29; // -
    // int LLOAD_0 = 30; // -
    // int LLOAD_1 = 31; // -
    // int LLOAD_2 = 32; // -
    // int LLOAD_3 = 33; // -
    // int FLOAD_0 = 34; // -
    // int FLOAD_1 = 35; // -
    // int FLOAD_2 = 36; // -
    // int FLOAD_3 = 37; // -
    // int DLOAD_0 = 38; // -
    // int DLOAD_1 = 39; // -
    // int DLOAD_2 = 40; // -
    // int DLOAD_3 = 41; // -
    // int ALOAD_0 = 42; // -
    // int ALOAD_1 = 43; // -
    // int ALOAD_2 = 44; // -
    // int ALOAD_3 = 45; // -
    /** The iaload. */
    int IALOAD = 46; // visitInsn
    /** The laload. */
    int LALOAD = 47; // -
    /** The faload. */
    int FALOAD = 48; // -
    /** The daload. */
    int DALOAD = 49; // -
    /** The aaload. */
    int AALOAD = 50; // -
    /** The baload. */
    int BALOAD = 51; // -
    /** The caload. */
    int CALOAD = 52; // -
    /** The saload. */
    int SALOAD = 53; // -
    /** The istore. */
    int ISTORE = 54; // visitVarInsn
    /** The lstore. */
    int LSTORE = 55; // -
    /** The fstore. */
    int FSTORE = 56; // -
    /** The dstore. */
    int DSTORE = 57; // -
    /** The astore. */
    int ASTORE = 58; // -
    // int ISTORE_0 = 59; // -
    // int ISTORE_1 = 60; // -
    // int ISTORE_2 = 61; // -
    // int ISTORE_3 = 62; // -
    // int LSTORE_0 = 63; // -
    // int LSTORE_1 = 64; // -
    // int LSTORE_2 = 65; // -
    // int LSTORE_3 = 66; // -
    // int FSTORE_0 = 67; // -
    // int FSTORE_1 = 68; // -
    // int FSTORE_2 = 69; // -
    // int FSTORE_3 = 70; // -
    // int DSTORE_0 = 71; // -
    // int DSTORE_1 = 72; // -
    // int DSTORE_2 = 73; // -
    // int DSTORE_3 = 74; // -
    // int ASTORE_0 = 75; // -
    // int ASTORE_1 = 76; // -
    // int ASTORE_2 = 77; // -
    // int ASTORE_3 = 78; // -
    /** The iastore. */
    int IASTORE = 79; // visitInsn
    /** The lastore. */
    int LASTORE = 80; // -
    /** The fastore. */
    int FASTORE = 81; // -
    /** The dastore. */
    int DASTORE = 82; // -
    /** The aastore. */
    int AASTORE = 83; // -
    /** The bastore. */
    int BASTORE = 84; // -
    /** The castore. */
    int CASTORE = 85; // -
    /** The sastore. */
    int SASTORE = 86; // -
    /** The pop. */
    int POP = 87; // -
    /** The pop2. */
    int POP2 = 88; // -
    /** The dup. */
    int DUP = 89; // -
    /** The dup x1. */
    int DUP_X1 = 90; // -
    /** The dup x2. */
    int DUP_X2 = 91; // -
    /** The dup2. */
    int DUP2 = 92; // -
    /** The dup2 x1. */
    int DUP2_X1 = 93; // -
    /** The dup2 x2. */
    int DUP2_X2 = 94; // -
    /** The swap. */
    int SWAP = 95; // -
    /** The iadd. */
    int IADD = 96; // -
    /** The ladd. */
    int LADD = 97; // -
    /** The fadd. */
    int FADD = 98; // -
    /** The dadd. */
    int DADD = 99; // -
    /** The isub. */
    int ISUB = 100; // -
    /** The lsub. */
    int LSUB = 101; // -
    /** The fsub. */
    int FSUB = 102; // -
    /** The dsub. */
    int DSUB = 103; // -
    /** The imul. */
    int IMUL = 104; // -
    /** The lmul. */
    int LMUL = 105; // -
    /** The fmul. */
    int FMUL = 106; // -
    /** The dmul. */
    int DMUL = 107; // -
    /** The idiv. */
    int IDIV = 108; // -
    /** The ldiv. */
    int LDIV = 109; // -
    /** The fdiv. */
    int FDIV = 110; // -
    /** The ddiv. */
    int DDIV = 111; // -
    /** The irem. */
    int IREM = 112; // -
    /** The lrem. */
    int LREM = 113; // -
    /** The frem. */
    int FREM = 114; // -
    /** The drem. */
    int DREM = 115; // -
    /** The ineg. */
    int INEG = 116; // -
    /** The lneg. */
    int LNEG = 117; // -
    /** The fneg. */
    int FNEG = 118; // -
    /** The dneg. */
    int DNEG = 119; // -
    /** The ishl. */
    int ISHL = 120; // -
    /** The lshl. */
    int LSHL = 121; // -
    /** The ishr. */
    int ISHR = 122; // -
    /** The lshr. */
    int LSHR = 123; // -
    /** The iushr. */
    int IUSHR = 124; // -
    /** The lushr. */
    int LUSHR = 125; // -
    /** The iand. */
    int IAND = 126; // -
    /** The land. */
    int LAND = 127; // -
    /** The ior. */
    int IOR = 128; // -
    /** The lor. */
    int LOR = 129; // -
    /** The ixor. */
    int IXOR = 130; // -
    /** The lxor. */
    int LXOR = 131; // -
    /** The iinc. */
    int IINC = 132; // visitIincInsn
    /** The i2l. */
    int I2L = 133; // visitInsn
    /** The i2f. */
    int I2F = 134; // -
    /** The i2d. */
    int I2D = 135; // -
    /** The l2i. */
    int L2I = 136; // -
    /** The l2f. */
    int L2F = 137; // -
    /** The l2d. */
    int L2D = 138; // -
    /** The f2i. */
    int F2I = 139; // -
    /** The f2l. */
    int F2L = 140; // -
    /** The f2d. */
    int F2D = 141; // -
    /** The d2i. */
    int D2I = 142; // -
    /** The d2l. */
    int D2L = 143; // -
    /** The d2f. */
    int D2F = 144; // -
    /** The i2b. */
    int I2B = 145; // -
    /** The i2c. */
    int I2C = 146; // -
    /** The i2s. */
    int I2S = 147; // -
    /** The lcmp. */
    int LCMP = 148; // -
    /** The fcmpl. */
    int FCMPL = 149; // -
    /** The fcmpg. */
    int FCMPG = 150; // -
    /** The dcmpl. */
    int DCMPL = 151; // -
    /** The dcmpg. */
    int DCMPG = 152; // -
    /** The ifeq. */
    int IFEQ = 153; // visitJumpInsn
    /** The ifne. */
    int IFNE = 154; // -
    /** The iflt. */
    int IFLT = 155; // -
    /** The ifge. */
    int IFGE = 156; // -
    /** The ifgt. */
    int IFGT = 157; // -
    /** The ifle. */
    int IFLE = 158; // -
    /** The if icmpeq. */
    int IF_ICMPEQ = 159; // -
    /** The if icmpne. */
    int IF_ICMPNE = 160; // -
    /** The if icmplt. */
    int IF_ICMPLT = 161; // -
    /** The if icmpge. */
    int IF_ICMPGE = 162; // -
    /** The if icmpgt. */
    int IF_ICMPGT = 163; // -
    /** The if icmple. */
    int IF_ICMPLE = 164; // -
    /** The if acmpeq. */
    int IF_ACMPEQ = 165; // -
    /** The if acmpne. */
    int IF_ACMPNE = 166; // -
    /** The goto. */
    int GOTO = 167; // -
    /** The jsr. */
    int JSR = 168; // -
    /** The ret. */
    int RET = 169; // visitVarInsn
    /** The tableswitch. */
    int TABLESWITCH = 170; // visiTableSwitchInsn
    /** The lookupswitch. */
    int LOOKUPSWITCH = 171; // visitLookupSwitch
    /** The ireturn. */
    int IRETURN = 172; // visitInsn
    /** The lreturn. */
    int LRETURN = 173; // -
    /** The freturn. */
    int FRETURN = 174; // -
    /** The dreturn. */
    int DRETURN = 175; // -
    /** The areturn. */
    int ARETURN = 176; // -
    /** The return. */
    int RETURN = 177; // -
    /** The getstatic. */
    int GETSTATIC = 178; // visitFieldInsn
    /** The putstatic. */
    int PUTSTATIC = 179; // -
    /** The getfield. */
    int GETFIELD = 180; // -
    /** The putfield. */
    int PUTFIELD = 181; // -
    /** The invokevirtual. */
    int INVOKEVIRTUAL = 182; // visitMethodInsn
    /** The invokespecial. */
    int INVOKESPECIAL = 183; // -
    /** The invokestatic. */
    int INVOKESTATIC = 184; // -
    /** The invokeinterface. */
    int INVOKEINTERFACE = 185; // -
    // int UNUSED = 186; // NOT VISITED
    /** The new. */
    int NEW = 187; // visitTypeInsn
    /** The newarray. */
    int NEWARRAY = 188; // visitIntInsn
    /** The anewarray. */
    int ANEWARRAY = 189; // visitTypeInsn
    /** The arraylength. */
    int ARRAYLENGTH = 190; // visitInsn
    /** The athrow. */
    int ATHROW = 191; // -
    /** The checkcast. */
    int CHECKCAST = 192; // visitTypeInsn
    /** The instanceof. */
    int INSTANCEOF = 193; // -
    /** The monitorenter. */
    int MONITORENTER = 194; // visitInsn
    /** The monitorexit. */
    int MONITOREXIT = 195; // -
    // int WIDE = 196; // NOT VISITED
    /** The multianewarray. */
    int MULTIANEWARRAY = 197; // visitMultiANewArrayInsn
    /** The ifnull. */
    int IFNULL = 198; // visitJumpInsn
    /** The ifnonnull. */
    int IFNONNULL = 199; // -
    // int GOTO_W = 200; // -
    // int JSR_W = 201; // -
}

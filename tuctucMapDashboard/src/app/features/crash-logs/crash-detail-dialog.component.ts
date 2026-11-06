import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CrashLog } from '../../core/models/driver.model';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-crash-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="crash-dialog-header">
      <div class="title-with-badge">
        <mat-icon class="header-icon" [class.resolved]="data.crash.resolved">
          {{ data.crash.resolved ? 'check_circle' : 'error' }}
        </mat-icon>
        <div>
          <h2 mat-dialog-title class="dialog-title">
            {{ data.crash.error_type }}
          </h2>
          <span class="dialog-subtitle">
            Ocurrido el {{ data.crash.created_at | date:'dd/MM/yyyy HH:mm:ss' }} UTC
          </span>
        </div>
      </div>
      <button mat-icon-button mat-dialog-close class="close-btn">
        <mat-icon>close</mat-icon>
      </button>
    </div>

    <mat-dialog-content class="dialog-content custom-scroll">
      <!-- Mensaje de error -->
      @if (data.crash.error_message) {
        <div class="error-message-box">
          <mat-icon>warning</mat-icon>
          <span class="error-msg-text">{{ data.crash.error_message }}</span>
        </div>
      }

      <!-- Solución Registrada (si ya fue resuelto) -->
      @if (data.crash.resolved && data.crash.solution) {
        <div class="solution-box">
          <div class="solution-title">
            <mat-icon>task_alt</mat-icon>
            <span>Solución Registrada</span>
          </div>
          <p class="solution-text">{{ data.crash.solution }}</p>
        </div>
      }

      <!-- Grid de Metadatos del Dispositivo y Sistema -->
      <div class="metadata-grid">
        <div class="meta-card">
          <span class="meta-label">Dispositivo</span>
          <span class="meta-value">
            {{ data.crash.device_manufacturer }} {{ data.crash.device_model }}
          </span>
          <span class="meta-sub">Marca: {{ data.crash.device_brand }}</span>
        </div>

        <div class="meta-card">
          <span class="meta-label">Sistema Android</span>
          <span class="meta-value">Android {{ data.crash.android_version }}</span>
          <span class="meta-sub">API Level / SDK: {{ data.crash.sdk_int }}</span>
        </div>

        <div class="meta-card">
          <span class="meta-label">Versión de App</span>
          <span class="meta-value">v{{ data.crash.app_version }} (Build {{ data.crash.build_number }})</span>
          <span class="meta-sub">Modo: {{ data.crash.build_type | uppercase }}</span>
        </div>

        <div class="meta-card">
          <span class="meta-label">Usuario y Contexto</span>
          <span class="meta-value">{{ data.crash.user_email || 'Usuario Anónimo' }}</span>
          <span class="meta-sub">
            Rol: {{ data.crash.role || 'No autenticado' }} | Hilo: {{ data.crash.thread_name || 'main' }}
          </span>
        </div>
      </div>

      <!-- Visor de Stack Trace -->
      <div class="stacktrace-section">
        <div class="stacktrace-header">
          <div class="code-title">
            <mat-icon>terminal</mat-icon>
            <span>Pila de Llamadas Completa (Stack Trace)</span>
          </div>
          <button mat-flat-button class="copy-btn" (click)="copyStackTrace()">
            <mat-icon>content_copy</mat-icon>
            Copiar Traza
          </button>
        </div>
        <pre class="stacktrace-container"><code>{{ data.crash.stack_trace }}</code></pre>
      </div>

      <!-- Cuadro para ingresar solución si se está resolviendo -->
      @if (isResolving) {
        <div class="solution-input-card">
          <div class="input-card-header">
            <mat-icon>rate_review</mat-icon>
            <span>Explicación de la Solución Aplicada (máx. 300 caracteres)</span>
          </div>
          <textarea
            class="solution-textarea"
            [(ngModel)]="solutionText"
            maxlength="300"
            rows="3"
            placeholder="Explica qué causaba el fallo y cómo fue resuelto..."
          ></textarea>
          <div class="input-card-footer">
            <span class="char-counter" [class.counter-full]="solutionText.length >= 300">
              {{ solutionText.length }} / 300 caracteres
            </span>
            <div class="confirm-actions">
              <button mat-button class="btn-cancel" (click)="cancelResolving()">
                Cancelar
              </button>
              <button
                mat-flat-button
                class="btn-save-solution"
                [disabled]="!solutionText.trim()"
                (click)="confirmResolve()"
              >
                <mat-icon>check_circle</mat-icon>
                Guardar y Marcar Resuelto
              </button>
            </div>
          </div>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      @if (!isResolving) {
        @if (!data.crash.resolved) {
          <button mat-flat-button class="btn-resolve" (click)="startResolving()">
            <mat-icon>task_alt</mat-icon>
            Marcar como Resuelto
          </button>
        } @else {
          <button mat-flat-button class="btn-reopen" (click)="reopenCrash()">
            <mat-icon>replay</mat-icon>
            Reabrir Incidencia
          </button>
        }
      }
      <button mat-stroked-button mat-dialog-close>
        Cerrar
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .crash-dialog-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 24px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      background: #1e293b;
    }
    .title-with-badge {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .header-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      color: #ef4444;
      &.resolved {
        color: #10b981;
      }
    }
    .dialog-title {
      margin: 0;
      font-size: 1.15rem;
      font-weight: 700;
      color: #f1f5f9;
      font-family: monospace;
    }
    .dialog-subtitle {
      font-size: 0.8rem;
      color: #94a3b8;
    }
    .close-btn {
      color: #94a3b8;
    }
    .dialog-content {
      padding: 20px 24px;
      max-height: 75vh;
      background: #0f172a;
      color: #e2e8f0;
    }
    .error-message-box {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 12px 16px;
      border-radius: 8px;
      margin-bottom: 16px;
      color: #fca5a5;
      font-weight: 500;
      mat-icon {
        color: #ef4444;
      }
    }
    .error-msg-text {
      font-family: monospace;
      font-size: 0.9rem;
      word-break: break-all;
    }
    .solution-box {
      background: rgba(16, 185, 129, 0.1);
      border: 1px solid rgba(16, 185, 129, 0.35);
      border-radius: 8px;
      padding: 14px 16px;
      margin-bottom: 16px;
    }
    .solution-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.88rem;
      font-weight: 700;
      color: #34d399;
      margin-bottom: 6px;
      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }
    .solution-text {
      margin: 0;
      color: #e2e8f0;
      font-size: 0.88rem;
      line-height: 1.5;
    }
    .solution-input-card {
      margin-top: 16px;
      background: #1e293b;
      border: 1px solid rgba(16, 185, 129, 0.4);
      border-radius: 10px;
      padding: 16px;
    }
    .input-card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.88rem;
      font-weight: 600;
      color: #34d399;
      margin-bottom: 10px;
      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    }
    .solution-textarea {
      width: 100%;
      box-sizing: border-box;
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 6px;
      padding: 10px 12px;
      color: #f8fafc;
      font-family: inherit;
      font-size: 0.88rem;
      resize: vertical;
      &:focus {
        outline: none;
        border-color: #10b981;
      }
    }
    .input-card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 8px;
      flex-wrap: wrap;
      gap: 8px;
    }
    .char-counter {
      font-size: 0.78rem;
      color: #94a3b8;
      &.counter-full {
        color: #f87171;
        font-weight: 600;
      }
    }
    .confirm-actions {
      display: flex;
      gap: 8px;
    }
    .btn-save-solution {
      background: #059669 !important;
      color: #ffffff !important;
      font-size: 0.82rem;
      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }
    .btn-cancel {
      color: #94a3b8 !important;
    }
    .metadata-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }
    .meta-card {
      background: #1e293b;
      padding: 12px 14px;
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.05);
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .meta-label {
      font-size: 0.72rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
      font-weight: 600;
    }
    .meta-value {
      font-size: 0.95rem;
      font-weight: 600;
      color: #38bdf8;
    }
    .meta-sub {
      font-size: 0.75rem;
      color: #94a3b8;
    }
    .stacktrace-section {
      margin-top: 10px;
    }
    .stacktrace-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }
    .code-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.85rem;
      font-weight: 600;
      color: #cbd5e1;
      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #38bdf8;
      }
    }
    .copy-btn {
      background: #334155 !important;
      color: #f8fafc !important;
      font-size: 0.8rem;
      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        margin-right: 4px;
      }
    }
    .stacktrace-container {
      background: #020617;
      border: 1px solid #334155;
      border-radius: 8px;
      padding: 14px;
      margin: 0;
      overflow-x: auto;
      max-height: 380px;
      code {
        font-family: 'Consolas', 'Fira Code', 'Monaco', monospace;
        font-size: 0.82rem;
        line-height: 1.5;
        color: #f87171;
        white-space: pre;
      }
    }
    .dialog-actions {
      padding: 12px 24px;
      background: #1e293b;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      gap: 8px;
    }
    .btn-resolve {
      background: #059669 !important;
      color: #ffffff !important;
    }
    .btn-reopen {
      background: #d97706 !important;
      color: #ffffff !important;
    }
  `]
})
export class CrashDetailDialogComponent {
  private readonly snackBar = inject(MatSnackBar);
  private readonly supabaseService = inject(SupabaseService);
  readonly dialogRef = inject(MatDialogRef<CrashDetailDialogComponent>);
  readonly data = inject<{ crash: CrashLog }>(MAT_DIALOG_DATA);

  isResolving = false;
  solutionText = '';

  copyStackTrace(): void {
    if (this.data.crash.stack_trace) {
      navigator.clipboard.writeText(this.data.crash.stack_trace).then(() => {
        this.snackBar.open('Stack trace copiado al portapapeles', 'Cerrar', {
          duration: 3000,
          horizontalPosition: 'center',
          verticalPosition: 'bottom'
        });
      });
    }
  }

  startResolving(): void {
    this.isResolving = true;
    this.solutionText = this.data.crash.solution || '';
  }

  cancelResolving(): void {
    this.isResolving = false;
  }

  async confirmResolve(): Promise<void> {
    const trimmed = this.solutionText.trim();
    if (!trimmed) {
      this.snackBar.open('Debes ingresar una explicación de la solución', 'Cerrar', { duration: 3000 });
      return;
    }

    try {
      const finalSolution = trimmed.substring(0, 300);
      await this.supabaseService.setCrashLogResolved(this.data.crash.id, true, finalSolution);
      this.data.crash.resolved = true;
      this.data.crash.solution = finalSolution;
      this.isResolving = false;
      this.snackBar.open('Incidencia marcada como resuelta con solución guardada', 'Cerrar', { duration: 3000 });
      this.dialogRef.close({ updated: true });
    } catch {
      this.snackBar.open('Error al resolver la incidencia', 'Cerrar', { duration: 3000 });
    }
  }

  async reopenCrash(): Promise<void> {
    const confirmed = window.confirm('¿Deseas reabrir esta incidencia como pendiente? La solución actual quedará limpia.');
    if (!confirmed) return;

    try {
      await this.supabaseService.setCrashLogResolved(this.data.crash.id, false);
      this.data.crash.resolved = false;
      this.data.crash.solution = null;
      this.snackBar.open('Incidencia reabierta como pendiente', 'Cerrar', { duration: 3000 });
      this.dialogRef.close({ updated: true });
    } catch {
      this.snackBar.open('Error al reabrir la incidencia', 'Cerrar', { duration: 3000 });
    }
  }
}



import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-change-name-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <mat-icon class="title-icon">badge</mat-icon>
      Cambiar Nombre
    </h2>
    
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      <mat-dialog-content class="dialog-content">
        <p class="subtitle">Ingresa tu nuevo nombre a continuación.</p>
        
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nuevo nombre</mat-label>
          <input matInput formControlName="name" placeholder="Ej. Juan Pérez">
          <mat-error *ngIf="form.get('name')?.hasError('required')">
            El nombre es obligatorio
          </mat-error>
        </mat-form-field>
      </mat-dialog-content>
      
      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button mat-dialog-close type="button">Cancelar</button>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || isSubmitting">
          {{ isSubmitting ? 'Guardando...' : 'Guardar' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .dialog-title {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #f8fafc;
      font-weight: 600;
      .title-icon { color: #60a5fa; }
    }
    .subtitle { color: #94a3b8; margin-bottom: 20px; }
    .full-width { width: 100%; }
    .dialog-content {
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
      overflow-y: visible;
    }
    .dialog-actions {
      padding: 0 24px 24px;
      display: flex;
      gap: 8px;
    }
    @media (max-width: 480px) {
      .dialog-actions {
        padding: 0 16px 16px;
      }
    }
  `]
})
export class ChangeNameDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly supabase = inject(SupabaseService);
  private readonly dialogRef = inject(MatDialogRef<ChangeNameDialogComponent>);
  private readonly snackBar = inject(MatSnackBar);

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]]
  });

  isSubmitting = false;

  async onSubmit() {
    if (this.form.invalid) return;
    this.isSubmitting = true;
    try {
      await this.supabase.updateAdminName(this.form.value.name);
      this.snackBar.open('Nombre actualizado correctamente', 'Cerrar', {
        duration: 3000,
        panelClass: 'snack-success'
      });
      this.dialogRef.close(this.form.value.name);
    } catch (err: any) {
      this.snackBar.open('Error al actualizar nombre', 'Cerrar', {
        duration: 4000,
        panelClass: 'snack-error'
      });
    } finally {
      this.isSubmitting = false;
    }
  }
}

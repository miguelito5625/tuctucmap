import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSliderModule } from '@angular/material/slider';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';

import { SupabaseService } from '../../services/supabase.service';
import { AppConfig } from '../../core/models/driver.model';

@Component({
  selector: 'app-business-config',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatSliderModule,
    MatDividerModule,
    MatChipsModule,
    DecimalPipe,
    DatePipe
  ],
  templateUrl: './business-config.component.html',
  styleUrl: './business-config.component.scss'
})
export class BusinessConfigComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly supabaseService = inject(SupabaseService);
  private readonly snackBar = inject(MatSnackBar);

  readonly isLoading = signal<boolean>(true);
  readonly isSaving = signal<boolean>(false);
  readonly lastUpdated = signal<string | null>(null);

  configForm: FormGroup = this.fb.group({
    admin_whatsapp: ['', [Validators.required, Validators.pattern(/^[0-9]{8,16}$/)]],
    account_holder_name: ['', [Validators.required, Validators.minLength(3)]],
    bank_1_name: ['', [Validators.required]],
    bank_1_account: ['', [Validators.required]],
    bank_2_name: ['', [Validators.required]],
    bank_2_account: ['', [Validators.required]],
    currency_symbol: ['Q', [Validators.required, Validators.maxLength(5)]],
    price_daily: [10.00, [Validators.required, Validators.min(0.01)]],
    price_weekly: [25.00, [Validators.required, Validators.min(0.01)]],
    price_quincenal: [45.00, [Validators.required, Validators.min(0.01)]],
    price_monthly: [80.00, [Validators.required, Validators.min(0.01)]],
    search_radius_km: [5.00, [Validators.required, Validators.min(1), Validators.max(100)]]
  });

  // Snapshot para detección de cambios
  private initialSnapshot: string = '';

  readonly hasChanges = signal<boolean>(false);

  // Computed values para el simulador móvil en tiempo real
  readonly previewCurrency = computed(() => this.configForm.get('currency_symbol')?.value || 'Q');
  readonly previewHolder = computed(() => this.configForm.get('account_holder_name')?.value || 'Administración TucTuc Map');
  readonly previewWhatsApp = computed(() => this.configForm.get('admin_whatsapp')?.value || '50200000000');
  readonly previewBank1Name = computed(() => this.configForm.get('bank_1_name')?.value || 'Banrural (Monetaria)');
  readonly previewBank1Account = computed(() => this.configForm.get('bank_1_account')?.value || '000-000000-0');
  readonly previewBank2Name = computed(() => this.configForm.get('bank_2_name')?.value || 'Banco Industrial (Ahorro)');
  readonly previewBank2Account = computed(() => this.configForm.get('bank_2_account')?.value || '000-000000-0');
  readonly previewPriceDaily = computed(() => Number(this.configForm.get('price_daily')?.value) || 10);
  readonly previewPriceWeekly = computed(() => Number(this.configForm.get('price_weekly')?.value) || 25);
  readonly previewPriceQuincenal = computed(() => Number(this.configForm.get('price_quincenal')?.value) || 45);
  readonly previewPriceMonthly = computed(() => Number(this.configForm.get('price_monthly')?.value) || 80);
  readonly previewRadius = computed(() => Number(this.configForm.get('search_radius_km')?.value) || 5);

  ngOnInit(): void {
    this.loadConfig();

    this.configForm.valueChanges.subscribe(() => {
      const currentJson = JSON.stringify(this.configForm.getRawValue());
      this.hasChanges.set(currentJson !== this.initialSnapshot);
    });
  }

  async loadConfig(): Promise<void> {
    this.isLoading.set(true);
    try {
      const cfg: AppConfig = await this.supabaseService.getAppConfig(true);
      this.configForm.patchValue({
        admin_whatsapp: cfg.admin_whatsapp || cfg.admin_whatsapp_phone || '50230000000',
        account_holder_name: cfg.account_holder_name || 'Administración TucTuc Map',
        bank_1_name: cfg.bank_1_name || 'Banrural (Monetaria)',
        bank_1_account: cfg.bank_1_account || cfg.banrural_account || '3402-0987-65',
        bank_2_name: cfg.bank_2_name || 'Banco Industrial (Ahorro)',
        bank_2_account: cfg.bank_2_account || cfg.bi_account || '027-019842-1',
        currency_symbol: cfg.currency_symbol || 'Q',
        price_daily: Number(cfg.price_daily) || 10.00,
        price_weekly: Number(cfg.price_weekly || cfg.price_semanal) || 25.00,
        price_quincenal: Number(cfg.price_quincenal) || 45.00,
        price_monthly: Number(cfg.price_monthly || cfg.price_mensual) || 80.00,
        search_radius_km: Number(cfg.search_radius_km || cfg.max_search_radius_km) || 5.00
      });

      this.initialSnapshot = JSON.stringify(this.configForm.getRawValue());
      this.hasChanges.set(false);
      this.lastUpdated.set(cfg.updated_at || null);
    } catch {
      this.snackBar.open('Error al cargar la configuración comercial desde Supabase', 'Cerrar', {
        duration: 4000,
        panelClass: ['snack-error']
      });
    } finally {
      this.isLoading.set(false);
    }
  }

  async saveConfig(): Promise<void> {
    if (this.configForm.invalid || this.isSaving()) {
      this.configForm.markAllAsTouched();
      this.snackBar.open('Por favor corrige los campos requeridos antes de guardar.', 'Entendido', {
        duration: 3500,
        panelClass: ['snack-warn']
      });
      return;
    }

    this.isSaving.set(true);
    try {
      const formVal = this.configForm.getRawValue();
      const updated = await this.supabaseService.updateAppConfig({
        admin_whatsapp: String(formVal.admin_whatsapp).trim(),
        admin_whatsapp_phone: String(formVal.admin_whatsapp).trim(),
        account_holder_name: String(formVal.account_holder_name).trim(),
        bank_1_name: String(formVal.bank_1_name).trim(),
        bank_1_account: String(formVal.bank_1_account).trim(),
        banrural_account: String(formVal.bank_1_account).trim(),
        bank_2_name: String(formVal.bank_2_name).trim(),
        bank_2_account: String(formVal.bank_2_account).trim(),
        bi_account: String(formVal.bank_2_account).trim(),
        currency_symbol: String(formVal.currency_symbol).trim(),
        price_daily: Number(formVal.price_daily),
        price_weekly: Number(formVal.price_weekly),
        price_semanal: Number(formVal.price_weekly),
        price_quincenal: Number(formVal.price_quincenal),
        price_monthly: Number(formVal.price_monthly),
        price_mensual: Number(formVal.price_monthly),
        search_radius_km: Number(formVal.search_radius_km),
        max_search_radius_km: Number(formVal.search_radius_km)
      });

      this.initialSnapshot = JSON.stringify(this.configForm.getRawValue());
      this.hasChanges.set(false);
      this.lastUpdated.set(updated.updated_at || new Date().toISOString());

      this.snackBar.open('¡Configuración comercial guardada con éxito! Se aplicó de inmediato en tiempo real.', 'Excelente', {
        duration: 4500,
        panelClass: ['snack-success']
      });
    } catch (err: any) {
      this.snackBar.open(`Error al guardar configuración: ${err?.message || 'Permiso denegado'}`, 'Cerrar', {
        duration: 5000,
        panelClass: ['snack-error']
      });
    } finally {
      this.isSaving.set(false);
    }
  }

  resetForm(): void {
    if (!this.hasChanges()) return;
    this.loadConfig();
    this.snackBar.open('Cambios no guardados descartados.', 'Cerrar', {
      duration: 2500
    });
  }

  testWhatsAppLink(): void {
    const rawPhone = this.configForm.get('admin_whatsapp')?.value || '';
    const cleanPhone = String(rawPhone).replace(/\D/g, '');
    if (!cleanPhone || cleanPhone.length < 8) {
      this.snackBar.open('Ingresa un número válido de WhatsApp para realizar la prueba.', 'Cerrar', {
        duration: 3000,
        panelClass: ['snack-warn']
      });
      return;
    }
    const testMsg = encodeURIComponent('Hola Administración TucTucMap, este es un mensaje de prueba desde el Dashboard.');
    const url = `https://api.whatsapp.com/send?phone=${cleanPhone}&text=${testMsg}`;
    window.open(url, '_blank');
  }

  copyToClipboard(text: string, label: string): void {
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open(`${label} copiado al portapapeles.`, 'OK', {
        duration: 2000
      });
    });
  }

  formatKm(value: number): string {
    return `${value} km`;
  }
}
